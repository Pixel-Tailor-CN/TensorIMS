package app.mystery0.ims.tensor

import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState

enum class EmbeddedAutoStartPhase { IDLE, DISABLED, CHECKING, READY, BUSY, RECOVERY, BLOCKED, NOTIFICATIONS_REQUIRED, LOCAL_NETWORK_REQUIRED, CREDENTIALS_REQUIRED, CONNECTING, FAILED, CANCELLED }

data class EmbeddedAutoStartState(val phase: EmbeddedAutoStartPhase = EmbeddedAutoStartPhase.IDLE, val error: String? = null) {
    /** 真实后端状态优先，已结束的启动结果不能将断开或恢复态显示为就绪。 */
    fun forBackend(status: BackendStatus): EmbeddedAutoStartState = when (status.connection) {
        ConnectionState.READY, ConnectionState.BUSY -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.READY)
        ConnectionState.RECOVERY_REQUIRED -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.RECOVERY)
        ConnectionState.BLOCKED -> EmbeddedAutoStartState(EmbeddedAutoStartPhase.BLOCKED)
        else -> if (phase == EmbeddedAutoStartPhase.READY) EmbeddedAutoStartState() else this
    }
}
