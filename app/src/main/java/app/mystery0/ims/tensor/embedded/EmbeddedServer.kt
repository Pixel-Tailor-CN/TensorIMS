package app.mystery0.ims.tensor.embedded

import android.app.Activity
import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.SystemClock
import android.util.Log
import app.mystery0.ims.tensor.bridge.*
import app.mystery0.ims.tensor.privilege.ExplicitPrivilegeSession
import app.mystery0.ims.tensor.privilege.OperationArguments
import app.mystery0.ims.tensor.privilege.OperationType
import java.util.UUID
import java.util.concurrent.Executors

/** 独立 shell/root 进程中的窄服务；没有命令执行、任意组件或通用 Binder 转发入口。 */
class EmbeddedServer(
    private val context: Context,
    private val identity: InstalledIdentity,
    private val challenge: String,
    private val exitOwnedProcess: () -> Unit,
) : IEmbeddedServer.Stub() {
    private val lock = Any()
    private val instanceId = UUID.randomUUID().toString()
    private val policy = BridgePolicy(identity.uid, identity.userId, identity.signer, challenge,
        SystemClock.elapsedRealtime(), BridgeProtocol.LEASE_MS)
    private val worker = Executors.newSingleThreadExecutor()
    private var clientToken: IBinder? = null
    private val death = IBinder.DeathRecipient {
        synchronized(lock) { policy.clientDied(SystemClock.elapsedRealtime()) }
    }
    private fun authenticate() {
        val uid = Binder.getCallingUid()
        identity.requireCurrent(context, uid)
        check(synchronized(lock) { policy.authenticate(uid, uid / 100000, identity.signer) }) { "AUTH_FAILED" }
    }
    override fun handshake(value: String, token: IBinder): Bundle = synchronized(lock) {
        authenticate()
        check(policy.handshake(value, SystemClock.elapsedRealtime())) { "AUTH_FAILED: stale challenge" }
        try { token.linkToDeath(death, 0); clientToken = token } catch (failure: Throwable) {
            policy.clientDied(SystemClock.elapsedRealtime()); throw failure
        }
        metadata().apply { putString(BridgeProtocol.CHALLENGE, value) }
    }
    override fun getStatus(): Bundle = synchronized(lock) {
        authenticate()
        check(clientToken?.isBinderAlive == true) { "AUTH_FAILED: client lease" }
        policy.confirmClient()
        metadata().apply {
            putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, !policy.isActive && !policy.cleanupFailed)
            if (policy.cleanupFailed) putString(BridgeProtocol.ERROR_CODE, "CLEANUP_FAILED")
            else if (policy.isActive) putString(BridgeProtocol.ERROR_CODE, "OPERATION_BUSY")
        }
    }
    override fun execute(request: Bundle, callback: IEmbeddedResult) {
        authenticate()
        require(request.keySet() == setOf(BridgeProtocol.PROTOCOL, BridgeProtocol.OPERATION_ID, BridgeProtocol.EPOCH,
            BridgeProtocol.OPERATION, BridgeProtocol.EXPIRES_AT, BridgeProtocol.ARGS)) { "INVALID_ARGUMENTS: envelope" }
        val operationId = requireNotNull(request.getString(BridgeProtocol.OPERATION_ID))
        val epoch = request.getLong(BridgeProtocol.EPOCH, -1)
        val operation = OperationType.entries.firstOrNull { it.name == request.getString(BridgeProtocol.OPERATION) }
            ?: throw IllegalArgumentException("INVALID_OPERATION")
        val arguments = OperationArguments.validate(operation, requireNotNull(request.getBundle(BridgeProtocol.ARGS)))
        synchronized(lock) {
            val error = policy.accept(request.getInt(BridgeProtocol.PROTOCOL), operationId, epoch, operation.name,
                request.getLong(BridgeProtocol.EXPIRES_AT), SystemClock.elapsedRealtime())
            check(error == null) { requireNotNull(error) }
        }
        worker.execute {
            val session = ExplicitPrivilegeSession(context, identity, operation, arguments, operationId, epoch, { it })
            val watcher = object : IInstrumentationWatcher.Stub() {
                override fun instrumentationStatus(name: ComponentName?, code: Int, results: Bundle?) = Unit
                override fun instrumentationFinished(name: ComponentName?, code: Int, results: Bundle?) {
                    val clean = session.finishAndConfirmCleanup()
                    val result = if (code == Activity.RESULT_OK && results != null) Bundle(results) else Bundle().apply {
                        putString(BridgeProtocol.ERROR_CODE, "INSTRUMENTATION_FAILED")
                        putString(BridgeProtocol.MESSAGE, "Instrumentation ended without a complete result")
                    }
                    session.failureCode?.let { result.putString(BridgeProtocol.ERROR_CODE, it) }
                    terminal(callback, operationId, epoch, result, clean)
                }
            }
            try {
                identity.requireCurrent(context, identity.uid)
                // 固定包/类映射与目标用户；清除应用调用身份后，系统看到启动器真实 shell/root UID。
                val token = Binder.clearCallingIdentity()
                val started = try {
                    val manager = IActivityManager.Stub.asInterface(requireNotNull(ServiceManager.getService(Context.ACTIVITY_SERVICE)))
                    manager.startInstrumentation(ComponentName(BridgeProtocol.PACKAGE, operation.componentClassName),
                        null, 8, session.attach(arguments), watcher, UiAutomationConnection(), identity.userId, null)
                } finally { Binder.restoreCallingIdentity(token) }
                if (!started) terminal(callback, operationId, epoch, error("START_REJECTED"), session.finishAndConfirmCleanup())
            } catch (failure: Throwable) {
                Log.e("TensorIMSBridge", "Instrumentation dispatch is indeterminate", failure)
                // 调用抛错可能发生在系统已启动之后，不能清锁或猜测杀进程。
                synchronized(lock) { policy.complete(operationId, epoch, false) }
                deliver(callback, operationId, epoch, error("OPERATION_INDETERMINATE").apply {
                    putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, false)
                })
            }
        }
    }
    private fun terminal(callback: IEmbeddedResult, id: String, epoch: Long, result: Bundle, clean: Boolean) {
        val matched = synchronized(lock) { policy.complete(id, epoch, clean) }
        if (!matched) return
        result.putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, clean)
        if (!clean) result.putString(BridgeProtocol.ERROR_CODE, "CLEANUP_FAILED")
        deliver(callback, id, epoch, result)
    }
    private fun deliver(callback: IEmbeddedResult, id: String, epoch: Long, result: Bundle) {
        try { callback.onResult(id, epoch, result) } catch (_: Throwable) {
            Log.w("TensorIMSBridge", "Client result delivery failed")
        }
    }
    override fun shutdownOwnedServer(requestedInstance: String): Boolean = synchronized(lock) {
        authenticate()
        if (requestedInstance != instanceId || !policy.requestShutdown()) return false
        clientToken?.unlinkToDeath(death, 0)
        clientToken = null
        // Binder 应答先发出；仅结束当前 Java 服务进程，绝不按名称或外部 PID 清理。
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ exitOwnedProcess() }, 150)
        true
    }
    fun checkIdleLease() {
        val exit = synchronized(lock) { policy.claimExpiredShutdown(SystemClock.elapsedRealtime()) }
        if (exit) exitOwnedProcess()
    }
    private fun metadata() = Bundle().apply {
        putInt(BridgeProtocol.PROTOCOL, BridgeProtocol.VERSION)
        putString(BridgeProtocol.INSTANCE_ID, instanceId)
        putInt(BridgeProtocol.RUNTIME_UID, Process.myUid())
        putLong(BridgeProtocol.VERSION_CODE, identity.versionCode)
        putString(BridgeProtocol.SIGNER, identity.signer)
        putString(BridgeProtocol.APK_PATH, identity.apkPath)
        putStringArrayList(BridgeProtocol.CAPABILITIES, ArrayList(OperationType.entries.map { it.name }))
    }
    private fun error(code: String) = Bundle().apply { putString(BridgeProtocol.ERROR_CODE, code) }
}
