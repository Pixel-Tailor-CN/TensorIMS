package app.mystery0.ims.tensor

import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAutoStartStateTest {
    private fun backend(connection: ConnectionState) = BackendStatus(BackendMode.EMBEDDED, connection)

    @Test fun previouslyReadyServiceCannotRemainReadyAfterDisconnect() {
        val state = EmbeddedAutoStartState(EmbeddedAutoStartPhase.READY)
        assertEquals(EmbeddedAutoStartPhase.IDLE, state.forBackend(backend(ConnectionState.DISCONNECTED)).phase)
    }

    @Test fun actualRecoveryAndBlockedStatesOverrideOldAttemptResults() {
        val state = EmbeddedAutoStartState(EmbeddedAutoStartPhase.READY)
        assertEquals(EmbeddedAutoStartPhase.RECOVERY, state.forBackend(backend(ConnectionState.RECOVERY_REQUIRED)).phase)
        assertEquals(EmbeddedAutoStartPhase.BLOCKED, state.forBackend(backend(ConnectionState.BLOCKED)).phase)
    }

    @Test fun authenticatedServiceOverridesOldFailureButDisconnectPreservesItsReason() {
        val state = EmbeddedAutoStartState(EmbeddedAutoStartPhase.FAILED, "TLS")
        assertEquals(EmbeddedAutoStartState(EmbeddedAutoStartPhase.READY), state.forBackend(backend(ConnectionState.READY)))
        assertEquals(state, state.forBackend(backend(ConnectionState.DISCONNECTED)))
    }
}
