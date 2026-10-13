package app.mystery0.ims.tensor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import app.mystery0.ims.tensor.embedded.EmbeddedAutoStartPermit
import app.mystery0.ims.tensor.embedded.WirelessAdbPhase
import app.mystery0.ims.tensor.embedded.WirelessAdbService
import app.mystery0.ims.tensor.embedded.adb.AdbIdentity
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.EmbeddedAutoStartDecision
import app.mystery0.ims.tensor.privilege.EmbeddedAutoStartPolicy
import app.mystery0.ims.tensor.privilege.EmbeddedForegroundTracker
import app.mystery0.ims.tensor.privilege.OperationCoordinator
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import app.mystery0.ims.tensor.ui.MainActivity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** 只接纳有效前台的有限 CONNECT；普通后台初始化、状态恢复及业务完成不创建新请求。 */
class EmbeddedAutoStartController(private val context: Context, private val settings: AppSettingsRepository) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = EmbeddedForegroundTracker()
    private val _state = MutableStateFlow(EmbeddedAutoStartState())
    val state = _state.asStateFlow()
    private var started = false
    private var checkJob: Job? = null
    private var backgroundJob: Job? = null
    @Volatile private var revision = 0L
    private var permit: EmbeddedAutoStartPermit? = null
    private var application: Application? = null

    fun start(application: Application) {
        if (started) return
        started = true
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            PrivilegeRuntime.connectionRefreshInProgress.collect { refreshing -> if (!refreshing) checkPending() }
        }
        scope.launch {
            PrivilegeRuntime.status.collect { status ->
                permit?.let { current ->
                    if (status.mode != BackendMode.EMBEDDED || status.epoch != current.epoch) cancelPending()
                }
                // 模式切换只撤销旧请求，不能成为自动启动触发点。
            }
        }
        scope.launch {
            WirelessAdbService.state.collect { wireless ->
                val current = permit ?: return@collect
                if (!wireless.automatic || wireless.requestId != current.requestId) return@collect
                _state.value = when {
                    wireless.active -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.CONNECTING)
                    wireless.phase == WirelessAdbPhase.SUCCESS -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.READY)
                    wireless.phase == WirelessAdbPhase.FAILED -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.FAILED, wireless.error)
                    else -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.CANCELLED)
                }
                if (!wireless.active) permit = null
            }
        }
    }

    /** 包括明确点击权限重试；相同 true 也代表一次新的用户启用许可。 */
    fun setEnabled(enabled: Boolean) {
        revision++
        cancelPending()
        settings.setAutoStartEnabled(enabled)
        if (enabled) {
            foreground.explicitEnable()
            _state.value = EmbeddedAutoStartState()
            checkPending()
        } else {
            foreground.suppress()
            _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.DISABLED)
        }
    }

    fun manualStart() {
        revision++
        foreground.suppress()
        cancelPending()
    }

    private fun decision(): EmbeddedAutoStartDecision = EmbeddedAutoStartPolicy.evaluate(
        PrivilegeRuntime.status.value, OperationCoordinator.busy.value || WirelessAdbService.state.value.active,
        PrivilegeRuntime.connectionRefreshInProgress.value,
    )

    private fun showDecision(value: EmbeddedAutoStartDecision) {
        val phase = when (value) {
            EmbeddedAutoStartDecision.SKIP_READY -> EmbeddedAutoStartPhase.READY
            EmbeddedAutoStartDecision.SKIP_RECOVERY -> EmbeddedAutoStartPhase.RECOVERY
            EmbeddedAutoStartDecision.SKIP_BLOCKED -> EmbeddedAutoStartPhase.BLOCKED
            EmbeddedAutoStartDecision.SKIP_BUSY -> EmbeddedAutoStartPhase.BUSY
            else -> EmbeddedAutoStartPhase.IDLE
        }
        _state.value = EmbeddedAutoStartState(phase)
        Log.i(TAG, "Automatic startup skipped: ${value.name}")
    }

    private fun checkPending() {
        if (!started || !settings.autoStartEnabled.value || !foreground.pending() || checkJob?.isActive == true) return
        val value = decision()
        if (value == EmbeddedAutoStartDecision.WAIT_REFRESH) return
        if (!foreground.consume()) return
        if (permit != null) { showDecision(EmbeddedAutoStartDecision.SKIP_BUSY); return }
        if (value != EmbeddedAutoStartDecision.START) { showDecision(value); return }
        val epoch = PrivilegeRuntime.status.value.epoch
        val expectedRevision = revision
        val generation = foreground.generation
        checkJob = scope.launch {
            try {
                _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CHECKING)
                if (!WirelessAdbService.notificationAvailable(context)) {
                    _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.NOTIFICATIONS_REQUIRED)
                    return@launch
                }
                if (Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.LOCAL_NETWORK_REQUIRED)
                    return@launch
                }
                val usable = withContext(Dispatchers.IO) {
                    runCatching { AdbIdentity.load(context, pairing = false) }.isSuccess
                }
                if (revision != expectedRevision || foreground.generation != generation || !foreground.hasResumedMain ||
                    !foreground.isForeground || !settings.autoStartEnabled.value || PrivilegeRuntime.status.value.epoch != epoch) {
                    if (revision == expectedRevision) _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CANCELLED)
                    return@launch
                }
                if (!usable) {
                    _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CREDENTIALS_REQUIRED)
                    return@launch
                }
                if (PrivilegeRuntime.connectionRefreshInProgress.value) {
                    PrivilegeRuntime.connectionRefreshInProgress.filter { !it }.first()
                }
                if (revision != expectedRevision || !foreground.hasResumedMain || foreground.generation != generation ||
                    !settings.autoStartEnabled.value || PrivilegeRuntime.status.value.epoch != epoch) {
                    if (revision == expectedRevision) _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CANCELLED)
                    return@launch
                }
                val currentDecision = decision()
                if (currentDecision != EmbeddedAutoStartDecision.START) { showDecision(currentDecision); return@launch }
                val issued = EmbeddedAutoStartPermit(UUID.randomUUID().toString(), epoch,
                    authorized = { revision == expectedRevision && settings.autoStartEnabled.value &&
                        PrivilegeRuntime.status.value.mode == BackendMode.EMBEDDED && PrivilegeRuntime.status.value.epoch == epoch },
                    foreground = { foreground.isForeground }, visible = { foreground.hasVisibleActivity })
                permit = issued
                if (WirelessAdbService.startAutomaticConnect(context, issued)) {
                    _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CONNECTING)
                    Log.i(TAG, "Automatic wireless startup admitted")
                } else {
                    issued.revoke()
                    if (permit === issued) permit = null
                    showDecision(decision())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Automatic startup preparation failed (${failure.javaClass.simpleName})")
                _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.FAILED)
            }
        }
    }

    private fun cancelPending(beforeBootstrapOnly: Boolean = false) {
        val checking = checkJob?.isActive == true
        checkJob?.cancel()
        checkJob = null
        val current = permit ?: run {
            if (checking) _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CANCELLED)
            return
        }
        if (current.revoke(beforeBootstrapOnly)) {
            WirelessAdbService.cancelAutomatic(context, current.requestId)
            if (permit === current) permit = null
            _state.value = EmbeddedAutoStartState(EmbeddedAutoStartPhase.CANCELLED)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        foreground.started(activity, SystemClock.elapsedRealtime())
        backgroundJob?.cancel()
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) { foreground.mainResumed(activity); checkPending() }
    }

    override fun onActivityPaused(activity: Activity) { if (activity is MainActivity) foreground.mainPaused(activity) }

    override fun onActivityStopped(activity: Activity) {
        foreground.stopped(activity, activity.isChangingConfigurations, SystemClock.elapsedRealtime())
        if (!foreground.hasVisibleActivity && !activity.isChangingConfigurations) {
            backgroundJob?.cancel()
            backgroundJob = scope.launch {
                delay(EmbeddedForegroundTracker.BACKGROUND_DELAY_MS)
                if (foreground.confirmBackground(SystemClock.elapsedRealtime())) cancelPending(beforeBootstrapOnly = true)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    fun close() {
        cancelPending()
        application?.unregisterActivityLifecycleCallbacks(this)
        scope.cancel()
    }

    companion object { private const val TAG = "EmbeddedAutoStart" }
}
