package app.mystery0.ims.tensor.privilege

/** 只接受本次开机的可靠终态，或系统开机计数证明的设备重启。 */
object RecoveryPolicy {
    fun oldOperationEnded(record: OperationRecord, currentBoot: Int): Boolean =
        (record.phase == OperationPhase.TERMINAL && record.cleanupConfirmed) ||
            (record.bootCount >= 0 && currentBoot >= 0 && record.bootCount != currentBoot)

    fun mayReconnect(record: OperationRecord?, mode: BackendMode, currentBoot: Int, taskActive: Boolean): Boolean =
        record != null && mode != BackendMode.UNSET && record.mode == mode && !taskActive && oldOperationEnded(record, currentBoot)
}
