package app.mystery0.ims.tensor.privilege

import org.junit.Assert.*
import org.junit.Test

class BrokerRetryPolicyTest {
    @Test fun emptyWriteResultCannotAuthorizeReplay() {
        assertFalse(BrokerRetryPolicy.allowed(false, false, null))
        assertFalse(BrokerRetryPolicy.allowed(false, true, null))
    }
    @Test fun errorTextAloneNeverAuthorizesFallback() {
        assertFalse(BrokerRetryPolicy.allowed(true, false, "PERMISSION_DENIED"))
    }
    @Test fun structuredPreWritePermissionDenialAllowsSameModeFallback() {
        assertTrue(BrokerRetryPolicy.allowed(true, true, null))
    }
    @Test fun unknownCleanupOrGlobalDelegationBusyOverrideRetryMarker() {
        listOf("DELEGATION_BUSY", "CLEANUP_FAILED", "OPERATION_INDETERMINATE").forEach {
            assertFalse(BrokerRetryPolicy.allowed(true, true, it))
        }
    }
}
