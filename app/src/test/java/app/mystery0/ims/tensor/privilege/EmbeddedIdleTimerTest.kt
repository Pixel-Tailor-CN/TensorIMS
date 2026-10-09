package app.mystery0.ims.tensor.privilege

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class EmbeddedIdleTimerTest {
    private data class Wait(val millis: Long, val release: CompletableDeferred<Unit>, val ended: CompletableDeferred<Unit>)

    @Test fun foregroundOrTaskCancelsAndNewIdlePeriodWaitsFullDuration() = runBlocking {
        withTimeout(5_000) {
            val events = MutableSharedFlow<EmbeddedIdleTimer.Request?>()
            val waits = Channel<Wait>(Channel.UNLIMITED)
            val stops = Channel<Long>(Channel.UNLIMITED)
            val timer = EmbeddedIdleTimer { millis ->
                val wait = Wait(millis, CompletableDeferred(), CompletableDeferred())
                waits.send(wait)
                try { wait.release.await() } finally { wait.ended.complete(Unit) }
            }
            val observer = launch { timer.observe(events) { stops.send(it.epoch) } }
            events.subscriptionCount.collectUntilSubscribed()
            events.emit(EmbeddedIdleTimer.Request(2, 7))
            val first = waits.receive()
            assertEquals(120_000L, first.millis)
            events.emit(null)
            first.ended.await()
            first.release.complete(Unit)
            assertTrue(stops.tryReceive().isFailure)
            events.emit(EmbeddedIdleTimer.Request(2, 7))
            val second = waits.receive()
            assertEquals(120_000L, second.millis)
            second.release.complete(Unit)
            assertEquals(7L, stops.receive())
            observer.cancel()
        }
    }

    @Test fun changingDelayOrBackendEpochInvalidatesOldTimer() = runBlocking {
        withTimeout(5_000) {
            val events = MutableSharedFlow<EmbeddedIdleTimer.Request?>()
            val waits = Channel<Wait>(Channel.UNLIMITED)
            val stops = Channel<Long>(Channel.UNLIMITED)
            val timer = EmbeddedIdleTimer { millis ->
                val wait = Wait(millis, CompletableDeferred(), CompletableDeferred())
                waits.send(wait)
                try { wait.release.await() } finally { wait.ended.complete(Unit) }
            }
            val observer = launch { timer.observe(events) { stops.send(it.epoch) } }
            events.subscriptionCount.collectUntilSubscribed()
            events.emit(EmbeddedIdleTimer.Request(2, 1))
            val first = waits.receive()
            events.emit(EmbeddedIdleTimer.Request(30, 2))
            first.ended.await()
            val second = waits.receive()
            assertEquals(1_800_000L, second.millis)
            first.release.complete(Unit)
            assertTrue(stops.tryReceive().isFailure)
            second.release.complete(Unit)
            assertEquals(2L, stops.receive())
            observer.cancel()
        }
    }
}

private suspend fun kotlinx.coroutines.flow.StateFlow<Int>.collectUntilSubscribed() {
    first { it > 0 }
}
