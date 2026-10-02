package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class BackendStateMachineTest {
    private fun ready() = BackendStateMachine(BackendMode.OFFICIAL).apply {
        updateConnection(status.copy(connection = ConnectionState.READY))
    }

    @Test fun timeoutDoesNotPermitDelayedPrimaryOrFallbackDispatch() {
        val machine = ready()
        machine.begin("primary")
        val epoch = machine.status.epoch
        assertTrue(machine.mayContinue("primary", epoch))
        machine.uncertain("primary", epoch)
        assertFalse(machine.mayContinue("primary", epoch))
        machine.finish("primary", epoch, true)
        assertFalse(machine.mayContinue("primary", epoch))
    }

    @Test fun switchFreezeClosesWriteGateBeforeDisconnect() {
        val machine = ready()
        val prior = machine.freezeForSwitch()
        assertNotNull(prior)
        assertFalse(machine.begin("racing-write"))
        assertTrue(machine.completeSwitch(BackendMode.EMBEDDED))
        assertEquals(BackendMode.EMBEDDED, machine.status.mode)
        assertTrue(machine.status.epoch > prior!!.epoch)
    }

    @Test fun failedShutdownRestoresOldModeWithoutOpeningAnotherBackend() {
        val machine = ready()
        val prior = machine.freezeForSwitch()!!
        machine.abortSwitch(prior, "SHUTDOWN_UNCONFIRMED", "retry")
        assertEquals(BackendMode.OFFICIAL, machine.status.mode)
        assertEquals(prior.epoch, machine.status.epoch)
        assertEquals("SHUTDOWN_UNCONFIRMED", machine.status.errorCode)
    }

    @Test fun connectionFreezeAndActiveOperationExcludeOneAnother() {
        val machine = ready()
        assertTrue(machine.beginConnection())
        assertFalse(machine.begin("write"))
        assertNull(machine.freezeForSwitch())
        machine.updateConnection(machine.status.copy(connection = ConnectionState.READY))
        assertTrue(machine.begin("write"))
        assertFalse(machine.beginConnection())
        assertNull(machine.freezeForSwitch())
    }

    @Test fun unsetNeverExecutes() {
        val machine = BackendStateMachine()
        assertFalse(machine.begin("write"))
        assertEquals(BackendMode.UNSET, machine.status.mode)
    }

    @Test fun deniedPermissionPreservesSelectedMode() {
        val machine = ready()
        machine.updateConnection(machine.status.copy(connection = ConnectionState.BLOCKED, errorCode = "PERMISSION_DENIED"))
        assertEquals(BackendMode.OFFICIAL, machine.status.mode)
        assertFalse(machine.begin("write"))
    }

    @Test fun busyRejectsSwitchWithoutQueuingIt() {
        val machine = ready()
        assertTrue(machine.begin("write"))
        assertNotNull(machine.choose(BackendMode.EMBEDDED))
        assertEquals(BackendMode.OFFICIAL, machine.status.mode)
        assertEquals(ConnectionState.BUSY, machine.status.connection)
    }

    @Test fun timeoutRetainsActiveOperationAndBlocksSwitch() {
        val machine = ready()
        machine.begin("write")
        machine.uncertain("write", machine.status.epoch)
        assertEquals("write", machine.activeOperationId)
        assertEquals(ConnectionState.RECOVERY_REQUIRED, machine.status.connection)
        assertFalse(machine.begin("next"))
        assertNotNull(machine.choose(BackendMode.EMBEDDED))
    }

    @Test fun staleEpochAndOperationCannotFinishCurrentOperation() {
        val machine = ready()
        machine.begin("write")
        assertFalse(machine.finish("write", machine.status.epoch - 1, true))
        assertFalse(machine.finish("another", machine.status.epoch, true))
        assertEquals("write", machine.activeOperationId)
    }

    @Test fun lateTerminalResultStillRequiresAuthenticatedReadback() {
        val machine = ready()
        machine.begin("write")
        val epoch = machine.status.epoch
        machine.uncertain("write", epoch)
        assertTrue(machine.finish("write", epoch, true))
        assertEquals(ConnectionState.RECOVERY_REQUIRED, machine.status.connection)
        assertFalse(machine.reconcile(true, false, true))
        assertFalse(machine.reconcile(true, true, false))
        assertTrue(machine.reconcile(true, true, true))
        assertEquals(ConnectionState.READY, machine.status.connection)
    }

    @Test fun reconstructedUnfinishedJournalBlocksWritesAndSwitch() {
        val machine = BackendStateMachine(BackendMode.EMBEDDED, unresolved = true)
        machine.updateConnection(machine.status.copy(connection = ConnectionState.READY))
        assertEquals(ConnectionState.RECOVERY_REQUIRED, machine.status.connection)
        assertFalse(machine.begin("write"))
        assertNotNull(machine.choose(BackendMode.OFFICIAL))
        assertFalse(machine.reconcile(false, true, true))
    }

    @Test fun cleanupFailureCannotBeClearedByConnectionEvent() {
        val machine = ready()
        machine.begin("write")
        assertFalse(machine.finish("write", machine.status.epoch, false))
        machine.updateConnection(machine.status.copy(connection = ConnectionState.READY))
        assertEquals(ConnectionState.RECOVERY_REQUIRED, machine.status.connection)
        assertFalse(machine.reconcile(false, true, true))
    }

    @Test fun previousModeConnectionEventCannotSelectOrReadyAnotherMode() {
        val machine = ready()
        val old = machine.status
        assertNull(machine.choose(BackendMode.EMBEDDED))
        machine.updateConnection(old)
        assertEquals(BackendMode.EMBEDDED, machine.status.mode)
        assertTrue(machine.status.epoch > old.epoch)
        assertFalse(machine.status.isReady)
    }
}
