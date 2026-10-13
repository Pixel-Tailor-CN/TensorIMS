package app.mystery0.ims.tensor.embedded

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.embedded.adb.AdbDiscoveryAmbiguousException
import app.mystery0.ims.tensor.embedded.adb.AdbServiceDiscovery
import app.mystery0.ims.tensor.embedded.adb.AdbServiceKind
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import app.mystery0.ims.tensor.privilege.EmbeddedForegroundTracker
import app.mystery0.ims.tensor.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.coroutineContext

enum class WirelessAdbPhase { IDLE, SEARCHING_PAIRING, WAITING_CODE, PAIRING, SEARCHING_CONNECT, STARTING, SUCCESS, FAILED }

data class WirelessAdbState(
    val active: Boolean = false,
    val phase: WirelessAdbPhase = WirelessAdbPhase.IDLE,
    val error: String? = null,
    // 仅代表本次通知会话完成配对，不持久化，也不推断系统当前是否仍授权。
    val pairedInSession: Boolean = false,
    val automatic: Boolean = false,
    val requestId: String? = null,
)

/** 用户明确启动的短时前台流程；通知直接接收验证码，不打开 Activity 或申请悬浮窗。 */
class WirelessAdbService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: String? = null
    private var epoch = -1L
    private var paired = false
    private var work: Job? = null
    private var modeWatch: Job? = null
    private var replies: Channel<String>? = null
    private var destroyed = false
    private var sessionStartId = 0
    private var startupRequest: StartupRequest? = null
    private val manager get() = getSystemService(NotificationManager::class.java)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        liveService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAIR, ACTION_CONNECT -> {
                val token = intent.dataString
                if (sessionGuard.owns(token) && token != session) {
                    // stopService 后的新请求可能先于旧 onDestroy 到达同一个实例。
                    discardLocalWork()
                    begin(intent.action == ACTION_PAIR, checkNotNull(token), startId)
                } else if (session == null) stopSelf(startId)
            }
            ACTION_REPLY -> receiveReply(intent, startId)
            ACTION_CANCEL -> intent.dataString?.let { cancelSession(it) }
            else -> if (session == null) stopSelf(startId)
        }
        // 绝不重投带验证码的 Intent；进程被杀后由用户重新开启流程。
        return START_NOT_STICKY
    }

    private fun begin(pair: Boolean, token: String, startId: Int) {
        if (destroyed || !sessionGuard.owns(token)) return
        session = token
        startupRequest = requestFor(token)
        if (startupRequest == null) { cancelSession(token); return }
        sessionStartId = startId
        epoch = PrivilegeRuntime.status.value.epoch
        val expectedEpoch = epoch
        paired = false
        replies = Channel(1)
        if (PrivilegeRuntime.status.value.mode != BackendMode.EMBEDDED || !permissionsGranted(this)) {
            fail(token, getString(R.string.wireless_service_permissions))
            return
        }
        startupRequest?.permit?.let { permit ->
            if (!permit.canContinue() || !PrivilegeRuntime.canAutomaticallyStartEmbedded(permit.epoch)) {
                fail(token, getString(R.string.embedded_auto_start_blocked))
                return
            }
        }
        try {
            createChannel(this)
            val phase = if (pair) WirelessAdbPhase.SEARCHING_PAIRING else WirelessAdbPhase.SEARCHING_CONNECT
            val initial = sessionState(active = true, phase = phase)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification(initial), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification(initial))
            }
            // 在前台服务真正建立之后通知界面，界面此时才可以打开系统设置。
            _state.value = initial
        } catch (failure: Exception) {
            Log.w(TAG, "Cannot start wireless foreground service (${failure.javaClass.simpleName})")
            fail(token, getString(R.string.wireless_service_unavailable))
            return
        }
        modeWatch = scope.launch {
            PrivilegeRuntime.status.collect { status ->
                if (status.mode != BackendMode.EMBEDDED || status.epoch != expectedEpoch) cancelSession(token)
            }
        }
        if (!owns(token)) { modeWatch?.cancel(); return }
        work = scope.launch {
            try {
                // Android 14+ shortService 最多约三分钟；预留取消与 Socket 清理时间。
                withTimeout(150_000L) {
                    if (pair && !pairFromNotification(token)) return@withTimeout
                    update(token, WirelessAdbPhase.SEARCHING_CONNECT)
                    val port = AdbServiceDiscovery(this@WirelessAdbService).findPort(AdbServiceKind.CONNECT, 20_000)
                    ensureSession(token)
                    startupRequest?.permit?.let { permit ->
                        // Activity 交接期间等待可见界面，不能在真实后台开始引导。
                        withTimeout(EmbeddedForegroundTracker.BACKGROUND_DELAY_MS) {
                            while (!permit.tryBeginBootstrap()) { ensureSession(token); delay(30) }
                        }
                    }
                    update(token, WirelessAdbPhase.STARTING)
                    val permit = startupRequest?.permit
                    val error = PrivilegeRuntime.startEmbedded(automaticEpoch = permit?.epoch, canStart = { permitted(token) }) {
                        ensureSession(token)
                        EmbeddedLauncher(this@WirelessAdbService).startWireless(port,
                            canContinue = { permitted(token) }, automatic = permit != null,
                            commitDelivery = permit?.let { it::commitDelivery })
                    }
                    ensureSession(token)
                    val confirmed = permit == null || PrivilegeRuntime.status.value.let {
                        it.mode == BackendMode.EMBEDDED && it.epoch == permit.epoch &&
                            it.connection in setOf(ConnectionState.READY, ConnectionState.BUSY)
                    }
                    if (error == null && confirmed) finish(token, WirelessAdbPhase.SUCCESS)
                    else if (error == null) fail(token, getString(R.string.embedded_auto_start_blocked))
                    else fail(token, error)
                }
            } catch (_: TimeoutCancellationException) {
                fail(token, getString(R.string.wireless_service_timeout))
            } catch (cancelled: CancellationException) {
                if (owns(token)) cancelSession(token)
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Wireless discovery failed (${failure.javaClass.simpleName})")
                fail(token, if (failure is AdbDiscoveryAmbiguousException) getString(R.string.wireless_service_ambiguous)
                    else getString(R.string.wireless_service_discovery_failed, failure.javaClass.simpleName))
            }
        }
    }

    private suspend fun pairFromNotification(token: String): Boolean {
        ensureSession(token)
        val incoming = checkNotNull(replies)
        AdbServiceDiscovery(this).findPort(AdbServiceKind.PAIRING, 120_000)
        ensureSession(token)
        update(token, WirelessAdbPhase.WAITING_CODE)
        while (true) {
            val code = incoming.receive()
            ensureSession(token)
            update(token, WirelessAdbPhase.PAIRING)
            // 配对窗口重开会换端口；不使用展示通知时的旧端口，也不使用连接端口。
            val port = AdbServiceDiscovery(this).findPort(AdbServiceKind.PAIRING, 10_000)
            val error = PrivilegeRuntime.startEmbedded {
                ensureSession(token)
                EmbeddedLauncher(this).pair(port, code)
            }
            ensureSession(token)
            if (error == null) { paired = true; return true }
            // 验证码失效或输入错误时仍留在通知里重试，不强迫用户离开系统设置。
            update(token, WirelessAdbPhase.WAITING_CODE, error)
        }
    }

    private fun receiveReply(intent: Intent, startId: Int) {
        val token = session
        if (!owns(token)) {
            if (session == null) stopSelf(startId)
            return
        }
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(INPUT_CODE)?.toString()?.trim()
        if (!WirelessReplyPolicy.accepts(session, intent.dataString, state.value.phase, code)) {
            if (token == intent.dataString && state.value.phase == WirelessAdbPhase.WAITING_CODE) {
                update(checkNotNull(token), WirelessAdbPhase.WAITING_CODE, getString(R.string.wireless_service_code_invalid))
            }
            return
        }
        // 先改变状态阻止快速双击；通知历史不回显、不保存验证码。
        update(checkNotNull(token), WirelessAdbPhase.PAIRING)
        if (replies?.trySend(checkNotNull(code))?.isSuccess != true) {
            update(token, WirelessAdbPhase.WAITING_CODE, getString(R.string.wireless_service_retry))
        }
    }

    private fun owns(token: String?) = !destroyed && token != null && token == session && sessionGuard.owns(token)
    private fun permitted(token: String): Boolean = owns(token) && startupRequest?.permit?.canContinue() != false

    private fun sessionState(active: Boolean = false, phase: WirelessAdbPhase, error: String? = null) = WirelessAdbState(
        active, phase, error, paired, startupRequest?.permit != null, startupRequest?.permit?.requestId,
    )

    private suspend fun ensureSession(token: String) {
        coroutineContext.ensureActive()
        if (!permitted(token) || PrivilegeRuntime.status.value.mode != BackendMode.EMBEDDED ||
            PrivilegeRuntime.status.value.epoch != epoch) throw CancellationException("Wireless session ended")
    }

    private fun update(token: String, phase: WirelessAdbPhase, error: String? = null) {
        if (!owns(token)) return
        // 首次找到配对服务时允许再次提醒；后续进度和输入重试仍只更新原通知。
        val alert = _state.value.phase == WirelessAdbPhase.SEARCHING_PAIRING &&
            phase == WirelessAdbPhase.WAITING_CODE
        val next = sessionState(active = true, phase = phase, error = error)
        _state.value = next
        manager.notify(NOTIFICATION_ID, notification(next, alert))
    }

    private fun fail(token: String, error: String) {
        if (!owns(token)) return
        finish(token, WirelessAdbPhase.FAILED,
            if (paired) getString(R.string.wireless_service_paired_connect_failed, error) else error)
    }

    private fun finish(token: String, phase: WirelessAdbPhase, error: String? = null) {
        if (!owns(token)) return
        val next = sessionState(phase = phase, error = error)
        _state.value = next
        replies?.close(); replies = null
        modeWatch?.cancel(); modeWatch = null
        // 完成通知仍可回看，已移除输入动作与敏感 RemoteInput 数据。
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (notificationAvailable(this)) manager.notify(NOTIFICATION_ID, notification(next))
        releaseRequest(token)
        session = null
        stopSelf()
    }

    private fun cancelSession(token: String) {
        if (!owns(token)) return
        startupRequest?.permit?.revoke()
        releaseRequest(token)
        _state.value = sessionState(phase = WirelessAdbPhase.IDLE)
        session = null
        discardLocalWork()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) {
        val token = session ?: return
        if (!owns(token) || startId < sessionStartId) return
        work?.cancel()
        fail(token, getString(R.string.wireless_service_timeout))
    }

    override fun onTimeout(startId: Int, fgsType: Int) = onTimeout(startId)

    override fun onDestroy() {
        val token = session
        val wasOwner = owns(token)
        // 先撤销归属再取消协程；旧超时异常可能在 NonCancellable 清理结束后才抛出。
        destroyed = true
        releaseRequest(token)
        session = null
        scope.cancel()
        replies?.close(); replies = null
        if (wasOwner && _state.value.active) _state.value = sessionState(
            phase = WirelessAdbPhase.FAILED, error = getString(R.string.wireless_service_interrupted),
        )
        if (liveService === this) liveService = null
        super.onDestroy()
    }

    private fun discardLocalWork() {
        work?.cancel(); work = null
        modeWatch?.cancel(); modeWatch = null
        replies?.close(); replies = null
    }

    private fun notification(current: WirelessAdbState, alert: Boolean = false): Notification {
        val text = current.error ?: getString(when (current.phase) {
            WirelessAdbPhase.SEARCHING_PAIRING -> R.string.wireless_service_searching_pairing
            WirelessAdbPhase.WAITING_CODE -> R.string.wireless_service_enter_code
            WirelessAdbPhase.PAIRING -> R.string.wireless_service_pairing
            WirelessAdbPhase.SEARCHING_CONNECT -> R.string.wireless_service_searching_connect
            WirelessAdbPhase.STARTING -> R.string.wireless_service_starting
            WirelessAdbPhase.SUCCESS -> R.string.wireless_service_success
            else -> R.string.wireless_service_interrupted
        })
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wireless_adb)
            .setContentTitle(getString(when {
                current.automatic -> R.string.embedded_auto_start_notification
                current.phase == WirelessAdbPhase.WAITING_CODE -> R.string.wireless_service_code_ready_title
                else -> R.string.wireless_service_title
            }))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(!alert)
            .setOngoing(current.active)
            .setAutoCancel(!current.active)
        // 输入配对码时仅保留标题，避免正文撑高通知后遮挡系统配对码。
        if (current.phase != WirelessAdbPhase.WAITING_CODE) {
            builder.setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
        }
        // 进行中的通知本体也不打开应用，避免误触关闭系统配对对话框。
        if (!current.active) builder.setContentIntent(PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (current.phase == WirelessAdbPhase.WAITING_CODE) {
            val input = RemoteInput.Builder(INPUT_CODE).setLabel(getString(R.string.wireless_service_code_label))
                .setAllowFreeFormInput(true).build()
            val reply = PendingIntent.getService(this, 1, actionIntent(ACTION_REPLY),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(null, getString(R.string.wireless_service_code_action), reply)
                .addRemoteInput(input).setAllowGeneratedReplies(false).build())
        }
        if (current.active) {
            val cancel = PendingIntent.getService(this, 2, actionIntent(ACTION_CANCEL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(null, getString(android.R.string.cancel), cancel).build())
        }
        return builder.build()
    }

    private fun actionIntent(action: String) = Intent(this, WirelessAdbService::class.java)
        .setAction(action).setData(Uri.parse(session))

    companion object {
        const val CHANNEL_ID = "embedded_wireless_adb"
        private const val TAG = "WirelessAdbService"
        private const val NOTIFICATION_ID = 8201
        private const val ACTION_PAIR = "pair"
        private const val ACTION_CONNECT = "connect"
        private const val ACTION_REPLY = "reply"
        private const val ACTION_CANCEL = "cancel"
        private const val INPUT_CODE = "pairing_code"
        private val _state = MutableStateFlow(WirelessAdbState())
        private val sessionGuard = WirelessSessionGuard()
        private data class StartupRequest(val token: String, val permit: EmbeddedAutoStartPermit?)
        @Volatile private var currentRequest: StartupRequest? = null
        private var liveService: WirelessAdbService? = null
        val state = _state.asStateFlow()

        fun startPairing(context: Context) = start(context, ACTION_PAIR)
        fun startConnect(context: Context) = start(context, ACTION_CONNECT)

        internal fun startAutomaticConnect(context: Context, permit: EmbeddedAutoStartPermit): Boolean = start(context, ACTION_CONNECT, permit)

        @Synchronized private fun requestFor(token: String): StartupRequest? = currentRequest?.takeIf { it.token == token && sessionGuard.owns(token) }
        @Synchronized private fun releaseRequest(token: String?) {
            // 会话结束同样撤销交付许可；先释放许可同步边界，再撤销 token 归属。
            currentRequest?.takeIf { it.token == token }?.permit?.revoke()
            sessionGuard.end(token)
            if (currentRequest?.token == token) currentRequest = null
        }

        @Synchronized private fun start(context: Context, action: String, permit: EmbeddedAutoStartPermit? = null): Boolean {
            if (_state.value.active || sessionGuard.current != null) return false
            if (permit != null && (!permit.canContinue() || !PrivilegeRuntime.canAutomaticallyStartEmbedded(permit.epoch))) return false
            if (!permissionsGranted(context)) {
                _state.value = WirelessAdbState(phase = WirelessAdbPhase.FAILED,
                    error = context.getString(R.string.wireless_service_permissions), automatic = permit != null, requestId = permit?.requestId)
                return false
            }
            val token = "tensorims-adb://session/${UUID.randomUUID()}"
            if (!sessionGuard.tryBegin(token, permit?.requestId)) return false
            currentRequest = StartupRequest(token, permit)
            _state.value = WirelessAdbState(active = true, automatic = permit != null, requestId = permit?.requestId)
            try {
                context.startForegroundService(Intent(context, WirelessAdbService::class.java)
                    .setAction(action).setData(Uri.parse(token)))
            } catch (failure: Exception) {
                Log.w(TAG, "Wireless service request failed (${failure.javaClass.simpleName})")
                if (sessionGuard.owns(token)) {
                    releaseRequest(token)
                    _state.value = WirelessAdbState(phase = WirelessAdbPhase.FAILED,
                        error = context.getString(R.string.wireless_service_unavailable), automatic = permit != null, requestId = permit?.requestId)
                }
                return false
            }
            return true
        }

        @Synchronized fun cancel(@Suppress("UNUSED_PARAMETER") context: Context) {
            cancelCurrent()
        }

        @Synchronized internal fun cancelAutomatic(@Suppress("UNUSED_PARAMETER") context: Context, requestId: String) {
            if (sessionGuard.ownsAutomatic(requestId)) cancelCurrent()
        }

        private fun cancelCurrent() {
            val request = currentRequest ?: return
            request.permit?.revoke()
            val running = liveService
            if (running?.owns(request.token) == true) running.cancelSession(request.token)
            else {
                // Intent 尚未送达时只撤销本次归属；迟到 Service 会自行退出，不能 stopService 伤及后续请求。
                releaseRequest(request.token)
                _state.value = WirelessAdbState(automatic = request.permit != null, requestId = request.permit?.requestId)
            }
        }

        fun notificationAvailable(context: Context): Boolean {
            createChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        }

        internal fun permissionsGranted(context: Context): Boolean = notificationAvailable(context) &&
            (Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)

        private fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.wireless_service_title), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    setShowBadge(false)
                })
        }
    }
}
