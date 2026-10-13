package app.mystery0.ims.tensor.privilege

enum class BackendMode { UNSET, OFFICIAL, EMBEDDED }
enum class ConnectionState { DISCONNECTED, CONNECTING, READY, BUSY, BLOCKED, SWITCHING, RECOVERY_REQUIRED }

enum class RecoveryReason { INTERRUPTED, TIMEOUT, CANCELLED, CLEANUP_UNCONFIRMED, OUTCOME_UNKNOWN, JOURNAL_IO, JOURNAL_CORRUPT }
enum class RecoveryStep { WAIT_TASK, RESTART_DEVICE, REBOOT_UNVERIFIABLE, CHECKPOINT_FAILED, CONNECT_BACKEND, READBACK_REQUIRED, VERIFYING, READBACK_FAILED, RESTORE_VOLTE, CONFIRM_JOURNAL }

/** 原因与下一步分开保存，迟到结果不会抹去最初触发暂停的原因。 */
data class BackendRecovery(
    val reason: RecoveryReason,
    val operation: OperationType? = null,
    val step: RecoveryStep,
    val connectionError: String? = null,
)

data class BackendStatus(
    val mode: BackendMode = BackendMode.UNSET,
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val epoch: Long = 0,
    val runtimeUid: Int? = null,
    val version: String? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val recovery: BackendRecovery? = null,
    val automaticRestorePaused: Boolean = false,
) {
    val isReady: Boolean get() = connection == ConnectionState.READY
}

/** 只有后端能证实操作没有启动时，才允许按普通失败完成日志。 */
class OperationNotStartedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 设备端是否结束或权限委托清理结果不明，不能释放写入安全门。 */
class OperationUncertainException(message: String, cause: Throwable? = null, val cleanupUnconfirmed: Boolean = false) : Exception(message, cause)
