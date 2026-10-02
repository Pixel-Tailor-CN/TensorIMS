package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test

class AdbResolveQueueTest {
    private class Backend {
        data class Call(val value: String, val done: (Result<String>) -> Unit, var cancelCount: Int = 0)
        val calls = mutableListOf<Call>()
        val queue = AdbResolveQueue<String> { value, done ->
            val call = Call(value, done).also(calls::add)
            DiscoveryCancellation { call.cancelCount++ }
        }
    }

    @Test fun startsOnlyOneSystemResolveAtATime() {
        val backend = Backend()
        val results = mutableListOf<String>()
        backend.queue.enqueue("one") { results += it.getOrThrow() }
        backend.queue.enqueue("two") { results += it.getOrThrow() }
        assertEquals(listOf("one"), backend.calls.map { it.value })
        backend.calls[0].done(Result.success("first"))
        assertEquals(listOf("one", "two"), backend.calls.map { it.value })
        backend.calls[1].done(Result.success("second"))
        assertEquals(listOf("first", "second"), results)
    }

    @Test fun cancelledLegacyResolveRetainsSlotUntilItsTerminalCallback() {
        val backend = Backend()
        val results = mutableListOf<String>()
        val first = backend.queue.enqueue("old") { results += it.getOrThrow() }
        first.cancel()
        first.cancel()
        backend.queue.enqueue("new") { results += it.getOrThrow() }
        assertEquals(1, backend.calls.size)
        assertEquals(1, backend.calls[0].cancelCount)
        backend.calls[0].done(Result.success("stale"))
        assertEquals(2, backend.calls.size)
        assertTrue(results.isEmpty())
        backend.calls[1].done(Result.success("current"))
        assertEquals(listOf("current"), results)
    }

    @Test fun cancellingQueuedRequestNeverStartsIt() {
        val backend = Backend()
        backend.queue.enqueue("one") {}
        val queued = backend.queue.enqueue("cancelled") { fail("Cancelled request completed") }
        backend.queue.enqueue("three") {}
        queued.cancel()
        backend.calls[0].done(Result.success("done"))
        assertEquals(listOf("one", "three"), backend.calls.map { it.value })
    }

    @Test fun duplicateOldTerminalCallbacksCannotReleaseCurrentSlot() {
        val backend = Backend()
        backend.queue.enqueue("one") {}
        backend.queue.enqueue("two") {}
        backend.queue.enqueue("three") {}
        backend.calls[0].done(Result.success("done"))
        backend.calls[0].done(Result.failure(AdbDiscoveryException("late stop")))
        assertEquals(listOf("one", "two"), backend.calls.map { it.value })
        backend.calls[1].done(Result.success("done"))
        assertEquals(listOf("one", "two", "three"), backend.calls.map { it.value })
    }

    @Test fun synchronousStartFailureDoesNotWedgeQueue() {
        val results = mutableListOf<Result<String>>()
        val queue = AdbResolveQueue<String> { value, done ->
            if (value == "bad") throw IllegalArgumentException("test")
            done(Result.success(value))
            DiscoveryCancellation {}
        }
        queue.enqueue("bad", results::add)
        queue.enqueue("next", results::add)
        assertTrue(results[0].exceptionOrNull() is IllegalArgumentException)
        assertEquals("next", results[1].getOrThrow())
    }
}
