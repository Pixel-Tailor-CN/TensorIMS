package app.mystery0.ims.tensor.privilege

/** 不依赖 Android 的安全状态机，由统一调度器串行调用。 */
class BackendStateMachine(mode: BackendMode = BackendMode.UNSET, unresolved: Boolean = false) {
    @Volatile var status = BackendStatus(
        mode = mode,
        connection = if (unresolved) ConnectionState.RECOVERY_REQUIRED else ConnectionState.DISCONNECTED,
        errorCode = if (unresolved) "RECOVERY_REQUIRED" else null,
        message = if (unresolved) "上次操作尚未确认结束，请先核对；必要时重启设备后重试" else null,
    )
        private set
    @Volatile var activeOperationId: String? = null
        private set

    @Synchronized fun choose(mode: BackendMode): String? {
        if (freezeForSwitch() == null) return "操作尚未结束或需要恢复，请完成核对后再切换"
        completeSwitch(mode)
        return null
    }

    /** 在任何解绑/关闭副作用之前原子冻结，继承锁上下文的后台任务也不能抢写。 */
    @Synchronized fun freezeForSwitch(): BackendStatus? {
        if (activeOperationId != null || status.connection in setOf(ConnectionState.BUSY,
                ConnectionState.RECOVERY_REQUIRED, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) return null
        val previous = status
        status = status.copy(connection = ConnectionState.SWITCHING)
        return previous
    }

    @Synchronized fun completeSwitch(mode: BackendMode): Boolean {
        if (activeOperationId != null || status.connection != ConnectionState.SWITCHING) return false
        status = BackendStatus(mode = mode, epoch = status.epoch + 1,
            connection = if (mode == BackendMode.UNSET) ConnectionState.DISCONNECTED else ConnectionState.CONNECTING)
        return true
    }

    @Synchronized fun abortSwitch(previous: BackendStatus, code: String, message: String) {
        if (activeOperationId == null && status.connection == ConnectionState.SWITCHING &&
            status.epoch == previous.epoch && status.mode == previous.mode) {
            status = previous.copy(connection = if (previous.mode == BackendMode.UNSET)
                ConnectionState.DISCONNECTED else ConnectionState.BLOCKED, errorCode = code, message = message)
        }
    }

    @Synchronized fun beginConnection(): Boolean {
        if (activeOperationId != null || status.mode == BackendMode.UNSET || status.connection in setOf(
                ConnectionState.BUSY, ConnectionState.RECOVERY_REQUIRED, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) return false
        status = status.copy(connection = ConnectionState.CONNECTING)
        return true
    }

    @Synchronized fun updateConnection(value: BackendStatus) {
        if (value.mode != status.mode || value.epoch != status.epoch) return
        if (activeOperationId != null || status.connection == ConnectionState.RECOVERY_REQUIRED) return
        status = value
    }

    @Synchronized fun begin(operationId: String): Boolean {
        if (status.mode == BackendMode.UNSET || !status.isReady || activeOperationId != null) return false
        activeOperationId = operationId
        status = status.copy(connection = ConnectionState.BUSY, errorCode = null, message = null)
        return true
    }

    @Synchronized fun mayContinue(operationId: String, epoch: Long): Boolean =
        operationId == activeOperationId && epoch == status.epoch && status.connection == ConnectionState.BUSY

    @Synchronized fun uncertain(operationId: String, epoch: Long) {
        if (operationId != activeOperationId || epoch != status.epoch) return
        status = status.copy(connection = ConnectionState.RECOVERY_REQUIRED, errorCode = "OPERATION_INDETERMINATE",
            message = "操作结果或权限清理尚未确认；不会自动重试，请等待旧任务结束后刷新核对")
    }

    @Synchronized fun finish(operationId: String, epoch: Long, cleanupConfirmed: Boolean): Boolean {
        if (operationId != activeOperationId || epoch != status.epoch) return false
        if (!cleanupConfirmed) {
            uncertain(operationId, epoch)
            return false
        }
        activeOperationId = null
        if (status.connection != ConnectionState.RECOVERY_REQUIRED) {
            status = status.copy(connection = ConnectionState.READY, errorCode = null, message = null)
        }
        return true
    }

    @Synchronized fun reconcile(terminalConfirmed: Boolean, authenticated: Boolean, readBackConfirmed: Boolean): Boolean {
        if (activeOperationId != null || !terminalConfirmed || !authenticated || !readBackConfirmed) return false
        if (status.mode == BackendMode.UNSET) return false
        status = status.copy(connection = ConnectionState.READY, errorCode = null,
            message = "已核对当前系统状态，未重放上次操作；自动恢复仍暂停，请核对后将开关关闭再开启")
        return true
    }
}
