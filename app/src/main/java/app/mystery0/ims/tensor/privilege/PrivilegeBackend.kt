package app.mystery0.ims.tensor.privilege

import android.content.Context
import android.os.Bundle

interface PrivilegeBackend {
    val mode: BackendMode
    suspend fun connect(context: Context, epoch: Long): BackendStatus
    /** 返回必须代表 watcher 已结束且清理已确认；客户端超时由 Runtime 独立处理。 */
    suspend fun execute(context: Context, operation: OperationType, arguments: Bundle, operationId: String, epoch: Long): Bundle?
    suspend fun disconnect(): Boolean
}
