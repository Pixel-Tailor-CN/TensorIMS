package app.mystery0.ims.tensor.privilege

enum class BackendMode { UNSET, OFFICIAL, EMBEDDED }
enum class ConnectionState { DISCONNECTED, CONNECTING, READY, BUSY, BLOCKED, SWITCHING, RECOVERY_REQUIRED }

data class BackendStatus(
    val mode: BackendMode = BackendMode.UNSET,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val epoch: Long = 0,
    val runtimeUid: Int? = null,
    val version: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
) {
    val isReady: Boolean get() = connection == ConnectionState.READY
}

/** 只有后端能证实操作没有启动时，才允许按普通失败完成日志。 */
class OperationNotStartedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 设备端是否结束或权限委托清理结果不明，不能释放写入安全门。 */
class OperationUncertainException(message: String, cause: Throwable? = null) : Exception(message, cause)
