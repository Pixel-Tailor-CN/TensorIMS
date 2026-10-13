package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class EmbeddedAutoStartPolicyTest {
    private fun status(connection: ConnectionState = ConnectionState.DISCONNECTED) = BackendStatus(BackendMode.EMBEDDED, connection)

    @Test fun onlyDisconnectedEmbeddedBackendCanStart() {
        assertEquals(EmbeddedAutoStartDecision.START, EmbeddedAutoStartPolicy.evaluate(status(), false, false))
        for (mode in listOf(BackendMode.UNSET, BackendMode.OFFICIAL)) {
            assertEquals(EmbeddedAutoStartDecision.SKIP_MODE, EmbeddedAutoStartPolicy.evaluate(status().copy(mode = mode), false, false))
        }
    }

    @Test fun completedOrUnsafeStatesNeverQueueAutomaticStartup() {
        assertEquals(EmbeddedAutoStartDecision.SKIP_READY, EmbeddedAutoStartPolicy.evaluate(status(ConnectionState.READY), false, false))
        assertEquals(EmbeddedAutoStartDecision.SKIP_RECOVERY, EmbeddedAutoStartPolicy.evaluate(status(ConnectionState.RECOVERY_REQUIRED), false, true))
        assertEquals(EmbeddedAutoStartDecision.SKIP_BLOCKED, EmbeddedAutoStartPolicy.evaluate(status(ConnectionState.BLOCKED), false, false))
        for (phase in listOf(ConnectionState.BUSY, ConnectionState.CONNECTING, ConnectionState.SWITCHING)) {
            assertEquals(EmbeddedAutoStartDecision.SKIP_BUSY, EmbeddedAutoStartPolicy.evaluate(status(phase), true, true))
        }
    }

    @Test fun connectionRefreshCanBeObservedButBusinessBusyIsNotQueued() {
        assertEquals(EmbeddedAutoStartDecision.WAIT_REFRESH, EmbeddedAutoStartPolicy.evaluate(status(), true, true))
        assertEquals(EmbeddedAutoStartDecision.SKIP_BUSY, EmbeddedAutoStartPolicy.evaluate(status(), true, false))
        assertEquals(EmbeddedAutoStartDecision.WAIT_REFRESH, EmbeddedAutoStartPolicy.evaluate(status(), false, true))
    }

    @Test fun automaticRestorePauseDoesNotGrantOrRevokeConnectionPermission() {
        assertEquals(EmbeddedAutoStartDecision.START,
            EmbeddedAutoStartPolicy.evaluate(status().copy(automaticRestorePaused = true), false, false))
    }
}
