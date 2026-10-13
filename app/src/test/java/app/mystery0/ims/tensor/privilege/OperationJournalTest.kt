package app.mystery0.ims.tensor.privilege

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class OperationJournalTest {
    @Test fun recoveryCauseSurvivesProcessReconstruction() {
        val dir = Files.createTempDirectory("tensorims-journal-cause").toFile()
        try {
            OperationJournal(dir).write(OperationRecord("timed-out-read", BackendMode.OFFICIAL, "READ_SIMS", 1, 20, -1,
                phase = OperationPhase.UNKNOWN, recoveryReason = RecoveryReason.TIMEOUT))
            assertEquals(RecoveryReason.TIMEOUT, OperationJournal(dir).read()!!.recoveryReason)
        } finally { dir.deleteRecursively() }
    }

    @Test fun unfinishedWriteSurvivesProcessReconstructionWithoutReplayData() {
        val dir = Files.createTempDirectory("tensorims-journal").toFile()
        try {
            val first = OperationJournal(dir)
            first.write(OperationRecord("operation-1", BackendMode.OFFICIAL, "SET_PERSISTENT_VOLTE", 5, 20, 1,
                "a".repeat(64), "persistent_volte_1.json"))
            val reconstructed = OperationJournal(dir).read()
            assertNotNull(reconstructed)
            assertEquals("operation-1", reconstructed!!.operationId)
            assertEquals(OperationPhase.PREPARED, reconstructed.phase)
            assertFalse(reconstructed.cleanupConfirmed)
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptJournalFailsClosedInsteadOfReturningNoPendingWrite() {
        val dir = Files.createTempDirectory("tensorims-journal").toFile()
        try {
            dir.resolve("operation.properties").writeText("truncated")
            assertThrows(IllegalStateException::class.java) { OperationJournal(dir).read() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun terminalIsDurableUntilReadbackAllowsExplicitClear() {
        val dir = Files.createTempDirectory("tensorims-journal").toFile()
        try {
            val journal = OperationJournal(dir)
            journal.write(OperationRecord("operation-2", BackendMode.EMBEDDED, "APPLY_CONFIG", 8, 21, 2,
                phase = OperationPhase.TERMINAL, cleanupConfirmed = true))
            assertEquals(OperationPhase.TERMINAL, OperationJournal(dir).read()!!.phase)
            journal.clear()
            assertNull(OperationJournal(dir).read())
        } finally { dir.deleteRecursively() }
    }
}
