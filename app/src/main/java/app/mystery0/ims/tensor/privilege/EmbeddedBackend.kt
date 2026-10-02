package app.mystery0.ims.tensor.privilege

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.IEmbeddedResult
import app.mystery0.ims.tensor.embedded.EmbeddedConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class EmbeddedBackend : PrivilegeBackend {
    override val mode = BackendMode.EMBEDDED
    private var instanceId: String? = null
    override suspend fun connect(context: Context, epoch: Long): BackendStatus = withContext(Dispatchers.IO) {
        val server = EmbeddedConnection.current() ?: return@withContext BackendStatus(mode, ConnectionState.DISCONNECTED,
            epoch, errorCode = "EMBEDDED_UNAVAILABLE", message = "请主动启动内置服务")
        try {
            val result = server.status
            check(result.getInt(BridgeProtocol.PROTOCOL) == BridgeProtocol.VERSION) { "VERSION_MISMATCH" }
            instanceId = requireNotNull(result.getString(BridgeProtocol.INSTANCE_ID))
            val error = result.getString(BridgeProtocol.ERROR_CODE)
            BackendStatus(mode, if (error == null) ConnectionState.READY else ConnectionState.RECOVERY_REQUIRED,
                epoch, result.getInt(BridgeProtocol.RUNTIME_UID), result.getLong(BridgeProtocol.VERSION_CODE).toString(),
                error, if (error != null) "旧操作或权限清理尚未确认" else null)
        } catch (_: Throwable) {
            BackendStatus(mode, ConnectionState.BLOCKED, epoch, errorCode = "AUTH_FAILED", message = "无法验证内置服务，请重新启动")
        }
    }
    override suspend fun execute(context: Context, operation: OperationType, arguments: Bundle, operationId: String, epoch: Long): Bundle? = withContext(Dispatchers.IO) {
        val server = EmbeddedConnection.current() ?: throw OperationNotStartedException("EMBEDDED_UNAVAILABLE")
        val args = try { OperationArguments.validate(operation, arguments) } catch (failure: Throwable) {
            throw OperationNotStartedException("INVALID_ARGUMENTS", failure)
        }
        val result = CompletableDeferred<Bundle>()
        val death = IBinder.DeathRecipient { result.completeExceptionally(OperationUncertainException("Private server died during operation")) }
        try {
            server.asBinder().linkToDeath(death, 0)
        } catch (failure: Throwable) { throw OperationNotStartedException("EMBEDDED_UNAVAILABLE", failure) }
        val callback = object : IEmbeddedResult.Stub() {
            override fun onResult(id: String, generation: Long, value: Bundle) {
                if (id == operationId && generation == epoch) result.complete(value)
            }
        }
        try {
            val request = Bundle().apply {
                putInt(BridgeProtocol.PROTOCOL, BridgeProtocol.VERSION)
                putString(BridgeProtocol.OPERATION_ID, operationId)
                putLong(BridgeProtocol.EPOCH, epoch)
                putString(BridgeProtocol.OPERATION, operation.name)
                putLong(BridgeProtocol.EXPIRES_AT, SystemClock.elapsedRealtime() + BridgeProtocol.REQUEST_MS)
                putBundle(BridgeProtocol.ARGS, args)
            }
            try { server.execute(request, callback) } catch (failure: Throwable) {
                // Binder 传输异常不能证明请求未抵达，日志必须保持未完成。
                throw OperationUncertainException("Private dispatch not confirmed", failure)
            }
            // 超时由 Runtime 向 UI 报告；此等待不取消设备端任务，也不释放协调器安全门。
            val finished = result.await()
            if (!finished.getBoolean(BridgeProtocol.CLEANUP_CONFIRMED)) throw OperationUncertainException("CLEANUP_FAILED")
            finished
        } finally { runCatching { server.asBinder().unlinkToDeath(death, 0) } }
    }
    override suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        val server = EmbeddedConnection.current() ?: return@withContext true
        val instance = instanceId ?: return@withContext false
        val died = CompletableDeferred<Unit>()
        val death = IBinder.DeathRecipient { died.complete(Unit) }
        try {
            server.asBinder().linkToDeath(death, 0)
            if (!server.shutdownOwnedServer(instance)) return@withContext false
            val confirmed = withTimeoutOrNull(5_000) { died.await(); true } ?: false
            if (confirmed) { EmbeddedConnection.clear(); instanceId = null }
            confirmed
        } catch (_: Throwable) { !server.asBinder().isBinderAlive }
        finally { runCatching { server.asBinder().unlinkToDeath(death, 0) } }
    }
}
