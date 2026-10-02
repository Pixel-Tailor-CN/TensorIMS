package app.mystery0.ims.tensor.embedded

import org.junit.Assert.*
import org.junit.Test

class WirelessSessionGuardTest {
    @Test fun noSessionAndNullTokenNeverOwnCallbacks() {
        val guard = WirelessSessionGuard()
        assertFalse(guard.owns(null))
        assertFalse(guard.owns("old"))
        assertFalse(guard.end(null))
    }

    @Test fun replacementRejectsOldTimeoutAndKeepsNewSession() {
        val guard = WirelessSessionGuard()
        guard.begin("old")
        guard.begin("new")
        assertFalse(guard.owns("old"))
        assertFalse(guard.end("old"))
        assertTrue(guard.owns("new"))
        assertEquals("new", guard.current)
    }

    @Test fun endedSessionCannotPublishAgain() {
        val guard = WirelessSessionGuard()
        guard.begin("session")
        assertTrue(guard.end("session"))
        assertFalse(guard.owns("session"))
        assertFalse(guard.end("session"))
        assertNull(guard.current)
    }

    @Test fun cancelledServiceDestroyCannotInvalidateNewPendingRequest() {
        val guard = WirelessSessionGuard()
        guard.begin("old-service")
        guard.end(guard.current)
        guard.begin("pending-new-service")
        assertFalse(guard.end("old-service"))
        assertTrue(guard.owns("pending-new-service"))
    }

    @Test fun staleCallbacksCannotMutateProtectedState() {
        val guard = WirelessSessionGuard()
        var state = "new notification"
        guard.begin("new")
        for (token in listOf(null, "old", "other")) {
            if (guard.owns(token)) state = "old timeout"
        }
        assertEquals("new notification", state)
        if (guard.owns("new")) state = "current result"
        assertEquals("current result", state)
    }
}
