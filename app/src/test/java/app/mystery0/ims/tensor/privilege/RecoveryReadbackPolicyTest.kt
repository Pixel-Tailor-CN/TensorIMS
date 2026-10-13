package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class RecoveryReadbackPolicyTest {
    @Test fun imsRegistrationDiagnosticDoesNotBlockConfirmedCriticalValues() {
        assertTrue(RecoveryReadbackPolicy.persistentVolteConfirmed(true, false, true, "IMS diagnostic unavailable", "IMS diagnostic unavailable"))
    }

    @Test fun criticalReadErrorStillBlocksEvenWhenDiagnosticWarningIsPresent() {
        assertFalse(RecoveryReadbackPolicy.persistentVolteConfirmed(true, false, true, "Backup read failed", "IMS diagnostic unavailable"))
    }

    @Test fun missingCriticalValuesCannotBeFilledByDiagnosticWarning() {
        assertFalse(RecoveryReadbackPolicy.persistentVolteConfirmed(true, false, false, "IMS diagnostic unavailable", "IMS diagnostic unavailable"))
    }

    @Test fun unsupportedFeatureStillRequiresConfirmedPermissionCleanup() {
        assertFalse(RecoveryReadbackPolicy.persistentVolteConfirmed(false, true, false, "Unsupported", null))
        assertTrue(RecoveryReadbackPolicy.persistentVolteConfirmed(true, true, false, "Unsupported", null))
    }
}
