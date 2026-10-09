package app.mystery0.ims.tensor.privilege

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class OperationCoordinatorTest {
    @Test fun quickCompletedTaskChangesIdleGenerationEvenWhenBusyReturnsToFalse() = runBlocking {
        val before = OperationCoordinator.completionVersion.value
        OperationCoordinator.serialized {
            OperationCoordinator.serialized { assertTrue(OperationCoordinator.busy.value) }
            assertEquals(before, OperationCoordinator.completionVersion.value)
        }
        assertFalse(OperationCoordinator.busy.value)
        assertEquals(before + 1, OperationCoordinator.completionVersion.value)
    }

    @Test fun childCoroutinesCannotAcquireTwoActiveBackendOperations() = runBlocking {
        val machine = BackendStateMachine(BackendMode.OFFICIAL).apply {
            updateConnection(status.copy(connection = ConnectionState.READY))
        }
        OperationCoordinator.serialized { coroutineScope {
            val release = CompletableDeferred<Unit>()
            val children = (1..16).map { number ->
                async(Dispatchers.Default) {
                    release.await()
                    OperationCoordinator.serialized { machine.begin("operation-$number") }
                }
            }
            release.complete(Unit)
            assertEquals(1, children.awaitAll().count { it })
        } }
        assertNotNull(machine.activeOperationId)
    }

    @Test fun switchIsRejectedRatherThanQueuedBehindAnOperation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val operation = async {
            OperationCoordinator.serialized { entered.complete(Unit); release.await() }
        }
        entered.await()
        assertTrue(OperationCoordinator.busy.value)
        var switched = false
        val result = OperationCoordinator.tryExclusive { switched = true; "switched" }
        assertNull(result)
        assertFalse(switched)
        release.complete(Unit)
        operation.await()
    }

    @Test fun businessFacadeCanEnterSameLockForPrimaryAndFallback() = runBlocking {
        val order = mutableListOf<String>()
        withTimeout(1000) {
            OperationCoordinator.serialized {
                assertTrue(OperationCoordinator.busy.value)
                OperationCoordinator.serialized { order += "primary" }
                OperationCoordinator.serialized { order += "fallback" }
                order += "history"
            }
        }
        assertEquals(listOf("primary", "fallback", "history"), order)
        assertFalse(OperationCoordinator.busy.value)
    }
}
