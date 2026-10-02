package app.mystery0.ims.tensor

import app.mystery0.ims.tensor.privilege.OperationCoordinator

/** 系统写入、fallback、本地历史和模式切换使用同一串行边界。 */
object ConfigurationOperations {
    val busy = OperationCoordinator.busy
    suspend fun <T> run(block: suspend () -> T): T = OperationCoordinator.serialized(block)
}
