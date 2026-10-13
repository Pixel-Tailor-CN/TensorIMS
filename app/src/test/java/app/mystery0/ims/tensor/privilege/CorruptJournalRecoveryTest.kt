package app.mystery0.ims.tensor.privilege

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CorruptJournalRecoveryTest {
    private fun withJournal(block: (File, File) -> Unit) {
        val root = Files.createTempDirectory("tensorims-corrupt-recovery").toFile()
        val directory = File(root, "privilege_operations").apply { mkdir() }
        File(directory, "operation.properties").writeText("unreadable original record")
        try { block(root, directory) } finally { root.deleteRecursively() }
    }

    @Test fun reopeningAppDoesNotReplaceRequiredDeviceReboot() = withJournal { _, directory ->
        CorruptJournalRecovery(directory).recordDetection(20)
        val reopened = CorruptJournalRecovery(directory)
        reopened.recordDetection(20)
        assertFalse(reopened.mayReconnect(20))
        reopened.recordDetection(21)
        assertTrue(reopened.mayReconnect(21))
    }

    @Test fun unknownBootCountDoesNotAllowRecovery() = withJournal { _, directory ->
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(-1)
        assertFalse(recovery.mayReconnect(21))
        recovery.recordDetection(21)
        assertFalse(recovery.mayReconnect(21))
        assertFalse(recovery.mayReconnect(-1))
        assertTrue(recovery.mayReconnect(22))
    }

    @Test fun interruptedVerificationRequiresAnotherDeviceReboot() = withJournal { _, directory ->
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(20)
        assertThrows(IllegalStateException::class.java) { recovery.beginVerification(20) }
        recovery.beginVerification(21)
        val reopened = CorruptJournalRecovery(directory)
        reopened.recordDetection(21)
        assertFalse(reopened.mayReconnect(21))
        assertThrows(IllegalStateException::class.java) { reopened.beginVerification(21) }
        assertTrue(reopened.mayReconnect(22))
    }

    @Test fun changedOriginalRecordInvalidatesRebootAndArchivePermission() = withJournal { _, directory ->
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(20)
        val fingerprint = recovery.beginVerification(21)
        File(directory, "operation.properties").appendText(" changed")
        assertFalse(recovery.mayReconnect(22))
        assertThrows(IllegalStateException::class.java) { recovery.archiveVerified(fingerprint) }
        assertTrue(File(directory, "operation.properties").exists())
        recovery.recordDetection(22)
        assertFalse(recovery.mayReconnect(22))
    }

    @Test fun damagedCheckpointRestartsRebootRequirementWithoutDeletingEvidence() = withJournal { _, directory ->
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(20)
        File(directory, "corrupt-recovery.properties").writeText("truncated")
        assertFalse(recovery.mayReconnect(21))
        recovery.recordDetection(21)
        assertFalse(recovery.mayReconnect(21))
        assertTrue(recovery.mayReconnect(22))
        assertEquals("unreadable original record", File(directory, "operation.properties").readText())
    }

    @Test fun interruptedCheckpointWriteCannotProveReboot() = withJournal { _, directory ->
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(20)
        File(directory, "corrupt-recovery.properties.new").writeText("partial")
        assertFalse(recovery.mayReconnect(21))
        recovery.recordDetection(21)
        assertFalse(recovery.mayReconnect(21))
        assertTrue(recovery.mayReconnect(22))
    }

    @Test fun archiveKeepsBothJournalFilesAndOriginalVolteBackup() = withJournal { root, directory ->
        File(directory, "operation.properties.new").writeText("unfinished new record")
        val backup = File(root, "persistent_volte_1.json").apply { writeText("original SIM values") }
        val recovery = CorruptJournalRecovery(directory)
        recovery.recordDetection(20)
        val fingerprint = recovery.beginVerification(21)
        val archive = recovery.archiveVerified(fingerprint)
        assertEquals("unreadable original record", File(archive, "operation.properties").readText())
        assertEquals("unfinished new record", File(archive, "operation.properties.new").readText())
        assertEquals("original SIM values", backup.readText())
        assertNull(OperationJournal(directory).read())
    }
}
