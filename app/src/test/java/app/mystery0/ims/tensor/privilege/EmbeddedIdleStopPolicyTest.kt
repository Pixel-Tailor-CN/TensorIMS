package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class EmbeddedIdleStopPolicyTest {
    private val ready = BackendStatus(BackendMode.EMBEDDED, ConnectionState.READY)

    @Test fun onlyEnabledBackgroundReadyEmbeddedServiceCanStop() {
        assertTrue(EmbeddedIdleStopPolicy.canSchedule(true, false, false, ready))
        assertFalse(EmbeddedIdleStopPolicy.canSchedule(false, false, false, ready))
        assertFalse(EmbeddedIdleStopPolicy.canSchedule(true, true, false, ready))
        assertFalse(EmbeddedIdleStopPolicy.canSchedule(true, false, true, ready))
        assertFalse(EmbeddedIdleStopPolicy.canSchedule(true, false, false, ready.copy(mode = BackendMode.OFFICIAL)))
        ConnectionState.entries.filter { it != ConnectionState.READY }.forEach {
            assertFalse(EmbeddedIdleStopPolicy.canSchedule(true, false, false, ready.copy(connection = it)))
        }
    }

    @Test fun delayInputRejectsEmptyFractionOverflowAndOutOfRange() {
        listOf("", " ", "0", "31", "-1", "2.5", "2a", "999999999999999999").forEach {
            assertNull(it, EmbeddedIdleStopPolicy.parseMinutes(it))
        }
        assertEquals(1, EmbeddedIdleStopPolicy.parseMinutes("1"))
        assertEquals(2, EmbeddedIdleStopPolicy.parseMinutes("2"))
        assertEquals(30, EmbeddedIdleStopPolicy.parseMinutes("30"))
    }

    @Test fun stoppingPreservesModeAndInvalidatesQueuedOldEpochRequests() {
        val machine = BackendStateMachine(BackendMode.EMBEDDED)
        machine.updateConnection(machine.status.copy(connection = ConnectionState.READY))
        val oldEpoch = machine.status.epoch
        assertNotNull(machine.freezeForSwitch())
        assertTrue(machine.completeIdleStop())
        assertEquals(BackendMode.EMBEDDED, machine.status.mode)
        assertEquals(ConnectionState.DISCONNECTED, machine.status.connection)
        assertEquals(oldEpoch + 1, machine.status.epoch)
        assertFalse(machine.begin("late-request"))
    }

    @Test fun activeOrUnknownOperationCannotBeStopped() {
        val machine = BackendStateMachine(BackendMode.EMBEDDED)
        machine.updateConnection(machine.status.copy(connection = ConnectionState.READY))
        assertTrue(machine.begin("write"))
        assertNull(machine.freezeForSwitch())
        assertFalse(machine.completeIdleStop())
        machine.uncertain("write", machine.status.epoch)
        machine.finish("write", machine.status.epoch, true)
        assertNull(machine.freezeForSwitch())
        assertFalse(machine.completeIdleStop())
    }
}
