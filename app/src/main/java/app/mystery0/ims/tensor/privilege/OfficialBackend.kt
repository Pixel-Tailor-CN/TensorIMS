package app.mystery0.ims.tensor.privilege

import android.app.Activity
import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import kotlinx.coroutines.CompletableDeferred
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

/** 官方 Binder 只在此适配器和显式官方会话内使用，绝不停止官方服务。 */
class OfficialBackend : PrivilegeBackend {
    override val mode = BackendMode.OFFICIAL
    @Volatile private var permissionPending = false
    private val retained = mutableMapOf<String, IInstrumentationWatcher>()

    fun observe(onChanged: () -> Unit) {
        Shizuku.addBinderReceivedListenerSticky { onChanged() }
        Shizuku.addBinderDeadListener { permissionPending = false; onChanged() }
        Shizuku.addRequestPermissionResultListener { _, _ -> permissionPending = false; onChanged() }
    }

    @Synchronized fun requestPermission() {
        if (permissionPending) return
        permissionPending = true
        try { Shizuku.requestPermission(0x4152) }
        catch (failure: Throwable) { permissionPending = false; throw failure }
    }
    fun canRequestAutomatically(): Boolean = try {
        !permissionPending && Shizuku.pingBinder() && !Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) { false }

    override suspend fun connect(context: Context, epoch: Long): BackendStatus = try {
        when {
            !Shizuku.pingBinder() -> BackendStatus(mode, ConnectionState.DISCONNECTED, epoch,
                errorCode = "NOT_RUNNING", message = "请先启动官方 Shizuku")
            Shizuku.isPreV11() -> BackendStatus(mode, ConnectionState.BLOCKED, epoch,
                errorCode = "VERSION_MISMATCH", message = "请更新官方 Shizuku")
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                BackendStatus(mode, ConnectionState.BLOCKED, epoch, errorCode = "PERMISSION_REQUIRED",
                    message = "请授予官方 Shizuku 权限")
            else -> BackendStatus(mode, ConnectionState.READY, epoch, runtimeUid = Shizuku.getUid(),
                version = Shizuku.getVersion().toString())
        }
    } catch (failure: Throwable) {
        Log.w(TAG, "Official connection check failed", failure)
        BackendStatus(mode, ConnectionState.DISCONNECTED, epoch, errorCode = "NOT_RUNNING",
            message = "无法连接官方 Shizuku，请检查服务状态")
    }

    override suspend fun execute(
        context: Context, operation: OperationType, arguments: Bundle, operationId: String, epoch: Long,
    ): Bundle? {
        val current = connect(context, epoch)
        if (!current.isReady) throw OperationNotStartedException(current.message ?: "Official backend is not ready")
        val activityManager = try {
            val binder = ServiceManager.getService(Context.ACTIVITY_SERVICE)
                ?: throw IllegalStateException("Activity service unavailable")
            IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
        } catch (failure: Throwable) {
            throw OperationNotStartedException("Activity service unavailable", failure)
        }
        val session = try { OfficialPrivilegeSession(context, operation, arguments, operationId, epoch) }
        catch (failure: IllegalArgumentException) { throw OperationNotStartedException("Invalid operation arguments", failure) }
        val deferred = CompletableDeferred<Bundle?>()
        val watcher = object : IInstrumentationWatcher.Stub() {
            override fun instrumentationStatus(name: ComponentName?, resultCode: Int, results: Bundle?) = Unit
            override fun instrumentationFinished(name: ComponentName?, resultCode: Int, results: Bundle?) {
                // watcher 终态与本会话的清理确认缺一不可；不能把空结果视作设备端仍在运行。
                val clean = session.finishAndConfirmCleanup()
                val result = if (resultCode == Activity.RESULT_OK) results?.let(::Bundle) else null
                session.failureCode?.let { result?.putString(BridgeProtocol.ERROR_CODE, it) }
                synchronized(retained) { retained.remove(operationId) }
                if (clean) {
                    result?.putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, true)
                    deferred.complete(result)
                } else deferred.completeExceptionally(OperationUncertainException("Permission delegation cleanup is unconfirmed"))
            }
        }
        synchronized(retained) { retained[operationId] = watcher }
        try {
            val started = activityManager.startInstrumentation(
                ComponentName(context.packageName, operation.componentClassName), null,
                8, // INSTR_FLAG_NO_RESTART：不重启主应用，保留协调器与恢复日志。
                session.attach(Bundle(arguments)), watcher, UiAutomationConnection(),
                android.os.Process.myUid() / 100_000, null,
            )
            if (!started) {
                synchronized(retained) { retained.remove(operationId) }
                if (!session.finishAndConfirmCleanup()) {
                    throw OperationUncertainException("Rejected instrumentation cleanup is unconfirmed")
                }
                throw OperationNotStartedException("Instrumentation start rejected")
            }
        } catch (failure: OperationNotStartedException) {
            throw failure
        } catch (failure: OperationUncertainException) {
            throw failure
        } catch (failure: Throwable) {
            // Binder 事务失败可能发生在系统已接受启动之后；保留 watcher，等待确切终态。
            Log.e(TAG, "Instrumentation dispatch outcome is unknown", failure)
        }
        // 超时属于调用者状态，不取消这个等待，也不提前撤销权限委托。
        return deferred.await()
    }

    override suspend fun disconnect(): Boolean {
        // 不调用 Shizuku.exit、stopService、kill 或任何官方服务关闭接口。
        return synchronized(retained) { retained.isEmpty() }
    }

    companion object { private const val TAG = "OfficialBackend" }
}
