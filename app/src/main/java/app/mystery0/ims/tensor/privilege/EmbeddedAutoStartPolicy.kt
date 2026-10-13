package app.mystery0.ims.tensor.privilege

internal enum class EmbeddedAutoStartDecision { START, WAIT_REFRESH, SKIP_MODE, SKIP_READY, SKIP_BUSY, SKIP_RECOVERY, SKIP_BLOCKED }

internal object EmbeddedAutoStartPolicy {
    /** 只允许普通连接；恢复重连的专门许可不能成为自动启动授权。 */
    fun evaluate(status: BackendStatus, busy: Boolean, refreshing: Boolean): EmbeddedAutoStartDecision = when {
        status.mode != BackendMode.EMBEDDED -> EmbeddedAutoStartDecision.SKIP_MODE
        status.isReady -> EmbeddedAutoStartDecision.SKIP_READY
        status.connection == ConnectionState.RECOVERY_REQUIRED -> EmbeddedAutoStartDecision.SKIP_RECOVERY
        status.connection == ConnectionState.BLOCKED -> EmbeddedAutoStartDecision.SKIP_BLOCKED
        status.connection != ConnectionState.DISCONNECTED -> EmbeddedAutoStartDecision.SKIP_BUSY
        refreshing -> EmbeddedAutoStartDecision.WAIT_REFRESH
        busy -> EmbeddedAutoStartDecision.SKIP_BUSY
        else -> EmbeddedAutoStartDecision.START
    }
}
