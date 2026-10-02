package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class RecoveryPolicyTest {
    private val record = OperationRecord("pending", BackendMode.EMBEDDED, "SET_PERSISTENT_VOLTE", 4, 20, 1,
        phase = OperationPhase.UNKNOWN, dispatched = true)

    @Test fun sameBootUnknownOperationCannotReconnect() {
        assertFalse(RecoveryPolicy.mayReconnect(record, BackendMode.EMBEDDED, 20, false))
    }
    @Test fun rebootAllowsExplicitSameModeReconnectWithoutLosingJournal() {
        assertTrue(RecoveryPolicy.mayReconnect(record, BackendMode.EMBEDDED, 21, false))
        assertEquals(OperationPhase.UNKNOWN, record.phase)
        assertTrue(record.dispatched)
    }
    @Test fun terminalWithoutCleanupCannotReconnect() {
        assertFalse(RecoveryPolicy.mayReconnect(record.copy(phase = OperationPhase.TERMINAL), BackendMode.EMBEDDED, 20, false))
    }
    @Test fun terminalAndCleanupPermitExplicitReconnect() {
        assertTrue(RecoveryPolicy.mayReconnect(record.copy(phase = OperationPhase.TERMINAL, cleanupConfirmed = true), BackendMode.EMBEDDED, 20, false))
    }
    @Test fun missingBootCounterDoesNotProveReboot() {
        assertFalse(RecoveryPolicy.oldOperationEnded(record, -1))
        assertFalse(RecoveryPolicy.oldOperationEnded(record.copy(bootCount = -1), 21))
    }
    @Test fun activeRecoveryAndDifferentModeRemainBlockedAfterReboot() {
        assertFalse(RecoveryPolicy.mayReconnect(record, BackendMode.EMBEDDED, 21, true))
        assertFalse(RecoveryPolicy.mayReconnect(record, BackendMode.OFFICIAL, 21, false))
    }
}
