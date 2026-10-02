package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class SimReadResultTest {
    @Test fun absentSubscriptionPayloadIsNotSuccessfulEmptyList() {
        assertNotNull(SimReadResult.fromPayload<String>(null).error)
    }
    @Test fun successfulEmptySubscriptionsRemainSuccessful() {
        val result = SimReadResult.fromPayload(emptyList<String>())
        assertNull(result.error)
        assertTrue(result.sims.isEmpty())
    }
    @Test fun delegationErrorOverridesPresentPayload() {
        val result = SimReadResult.fromPayload(listOf("SIM 1"), "DELEGATION_BUSY")
        assertEquals("DELEGATION_BUSY", result.error)
        assertTrue(result.sims.isEmpty())
    }
    @Test fun successfulSubscriptionsRetainSelectionData() {
        assertEquals(listOf("SIM 1", "SIM 2"), SimReadResult.fromPayload(listOf("SIM 1", "SIM 2")).sims)
    }
}
