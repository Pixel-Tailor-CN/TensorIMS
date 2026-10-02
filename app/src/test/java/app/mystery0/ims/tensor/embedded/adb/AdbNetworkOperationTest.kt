package app.mystery0.ims.tensor.embedded.adb

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AdbNetworkOperationTest {
    @Test fun timeoutClosesResourceBeforeWaitingForBlockedWorker() = runBlocking {
        val resource = BlockingResource()
        try {
            withAdbNetworkResource(resource, 100) { resource.waitUntilClosed() }
            fail("Expected timeout")
        } catch (_: TimeoutCancellationException) { }
        assertTrue(resource.closed.get())
        assertTrue(resource.workerExited.get())
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancellationClosesResourceAndDoesNotReturnSuccess() = runBlocking {
        val resource = BlockingResource()
        var completed = false
        val operation = launch {
            withAdbNetworkResource(resource) { resource.waitUntilClosed() }
            completed = true
        }
        withContext(Dispatchers.IO) { assertTrue(resource.workerStarted.await(3, TimeUnit.SECONDS)) }
        operation.cancelAndJoin()
        assertFalse(completed)
        assertTrue(resource.closed.get())
        assertTrue(resource.workerExited.get())
    }

    @Test fun ioFailureStillClosesResourceAndPreservesFailureType() = runBlocking {
        val resource = BlockingResource()
        val original = IOException("do not log credentials")
        try {
            withAdbNetworkResource(resource) { throw original }
            fail("Expected I/O failure")
        } catch (failure: IOException) {
            // 协程调试模式可能为恢复异步堆栈复制异常，但类别和业务信息必须保留。
            assertEquals(original.javaClass, failure.javaClass)
            assertEquals(original.message, failure.message)
        }
        assertTrue(resource.closed.get())
        assertTrue(currentCoroutineContext().isActive)
    }

    private class BlockingResource : Closeable {
        val workerStarted = CountDownLatch(1)
        val closed = AtomicBoolean(false)
        val workerExited = AtomicBoolean(false)
        private val unblocked = CountDownLatch(1)
        fun waitUntilClosed() {
            workerStarted.countDown()
            try { check(unblocked.await(3, TimeUnit.SECONDS)) { "Close must unblock the worker" } }
            finally { workerExited.set(true) }
        }
        override fun close() { closed.set(true); unblocked.countDown() }
    }
}
