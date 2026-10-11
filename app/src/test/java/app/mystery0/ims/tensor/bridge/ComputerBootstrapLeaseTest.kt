package app.mystery0.ims.tensor.bridge

import org.junit.Assert.*
import org.junit.Test

class ComputerBootstrapLeaseTest {
    private fun prepared(deadline: Long = 110_000L): ComputerBootstrapLease =
        ComputerBootstrapLease().also { it.prepare("challenge", 1, 7, "identity", deadline) }

    @Test fun claimRequiresPreparedCurrentShellOrRootLease() {
        assertNull(ComputerBootstrapLease().claim(1, 7, "identity", 2000))
        val lease = prepared()
        assertNull(lease.claim(1, 7, "identity", 1000))
        assertNull(lease.claim(1, 8, "identity", 2000))
        assertNull(lease.claim(1, 7, "other", 2000))
        assertNull(lease.claim(1, 7, "identity", 1000))
        assertEquals("challenge", lease.claim(1, 7, "identity", 2000))
    }

    @Test fun claimIsSingleUseAndDoesNotExtendDeadline() {
        val lease = prepared(10)
        assertEquals("challenge", lease.claim(9, 7, "identity", 2000))
        assertNull(lease.claim(9, 7, "identity", 2000))
        assertFalse(lease.permitsDelivery("challenge", 1, 10, 7, "identity", 2000))
    }

    @Test fun deliveryRequiresOriginalClaimAndCurrentGenerationIdentityEpochAndUid() {
        val lease = prepared()
        assertFalse(lease.permitsDelivery("challenge", 1, 2, 7, "identity", 2000))
        assertEquals("challenge", lease.claim(2, 7, "identity", 0))
        assertTrue(lease.permitsDelivery("challenge", 1, 3, 7, "identity", 0))
        assertFalse(lease.permitsDelivery("challenge", 1, 3, 7, "identity", 2000))
        assertFalse(lease.permitsDelivery("challenge", 2, 3, 7, "identity", 0))
        assertFalse(lease.permitsDelivery("challenge", 1, 3, 8, "identity", 0))
        assertFalse(lease.permitsDelivery("challenge", 1, 3, 7, "other", 0))
    }

    @Test fun revokeAndPrepareProtectNewerGenerationFromStaleWork() {
        val lease = prepared()
        lease.prepare("next", 2, 8, "next-identity", 200_000)
        lease.revoke(1)
        assertEquals("next", lease.claim(2, 8, "next-identity", 2000))
        lease.revoke(2)
        assertFalse(lease.permitsDelivery("next", 2, 3, 8, "next-identity", 2000))
    }

    @Test fun deadlineIsExclusiveAndClaimUidMustBeShellOrRoot() {
        val lease = prepared(10)
        assertNull(lease.claim(10, 7, "identity", 2000))
        val valid = prepared(10)
        assertEquals("challenge", valid.claim(9, 7, "identity", 2000))
        assertTrue(valid.permitsDelivery("challenge", 1, 9, 7, "identity", 2000))
        assertFalse(valid.permitsDelivery("challenge", 1, 10, 7, "identity", 2000))
    }
}
