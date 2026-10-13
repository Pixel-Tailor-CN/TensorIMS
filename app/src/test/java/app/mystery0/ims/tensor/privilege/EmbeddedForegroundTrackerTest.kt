package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class EmbeddedForegroundTrackerTest {
    private fun entered(): EmbeddedForegroundTracker = EmbeddedForegroundTracker().apply {
        started("main", 0)
        mainResumed("main")
    }

    @Test fun backgroundInitializationAndLogPageCannotClaimStartup() {
        val tracker = EmbeddedForegroundTracker()
        assertFalse(tracker.consume())
        tracker.started("logs", 0)
        assertFalse(tracker.consume())
        tracker.started("main", 1)
        tracker.mainResumed("main")
        assertTrue(tracker.consume())
        assertFalse(tracker.consume())
    }

    @Test fun rotationKeepsConsumedGenerationEvenWhenRecreationIsSlow() {
        val tracker = entered()
        assertTrue(tracker.consume())
        tracker.mainPaused("main")
        tracker.stopped("main", true, 10)
        assertFalse(tracker.confirmBackground(5_000))
        tracker.started("new-main", 5_000)
        tracker.mainResumed("new-main")
        assertEquals(1L, tracker.generation)
        assertFalse(tracker.consume())
    }

    @Test fun logActivityHandoffDoesNotProduceNewStartup() {
        val tracker = entered()
        assertTrue(tracker.consume())
        tracker.started("logs", 10)
        tracker.mainPaused("main")
        tracker.stopped("main", false, 11)
        assertFalse(tracker.confirmBackground(2_000))
        tracker.started("main", 2_001)
        tracker.mainResumed("main")
        tracker.stopped("logs", false, 2_002)
        assertFalse(tracker.consume())
    }

    @Test fun realBackgroundAt700MillisAllowsExactlyOneNewAttempt() {
        val tracker = entered()
        tracker.consume()
        tracker.mainPaused("main")
        tracker.stopped("main", false, 100)
        assertFalse(tracker.confirmBackground(799))
        assertTrue(tracker.confirmBackground(800))
        tracker.started("main", 801)
        tracker.mainResumed("main")
        assertEquals(2L, tracker.generation)
        assertTrue(tracker.consume())
        assertFalse(tracker.consume())
    }

    @Test fun delayedBackgroundCallbackCannotInvalidateNewForeground() {
        val tracker = entered()
        tracker.consume()
        tracker.stopped("main", false, 100)
        tracker.started("main", 1_000)
        tracker.mainResumed("main")
        assertEquals(2L, tracker.generation)
        assertFalse(tracker.confirmBackground(1_001))
        assertTrue(tracker.consume())
    }

    @Test fun explicitOptInCreatesOneNewPermissionAndManualActionConsumesIt() {
        val tracker = entered()
        tracker.consume()
        tracker.explicitEnable()
        assertTrue(tracker.consume())
        assertFalse(tracker.consume())
        tracker.explicitEnable()
        tracker.suppress()
        assertFalse(tracker.consume())
    }
}
