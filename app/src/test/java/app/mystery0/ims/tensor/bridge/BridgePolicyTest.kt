package app.mystery0.ims.tensor.bridge

import org.junit.Assert.*
import org.junit.Test

class BridgePolicyTest {
    private fun policy() = BridgePolicy(1012345, 10, "installed", "challenge", 100L, 1000L)

    @Test fun wrongUserUidAndSignerAreRejected() {
        val p = policy()
        assertFalse(p.authenticate(12345, 0, "installed"))
        assertFalse(p.authenticate(1012346, 10, "installed"))
        assertFalse(p.authenticate(1012345, 10, "replacement"))
        assertTrue(p.authenticate(1012345, 10, "installed"))
    }
    @Test fun challengeIsSingleUseAndExpires() {
        val p = policy()
        assertFalse(p.handshake("old", 120))
        assertTrue(p.handshake("challenge", 130))
        assertFalse(p.handshake("challenge", 140))
        assertFalse(policy().handshake("challenge", 1101))
    }
    @Test fun versionUnknownOperationReplayAndExpiryAreRejected() {
        val p = policy(); assertTrue(p.handshake("challenge", 110))
        assertEquals("VERSION_MISMATCH", p.accept(2, "one", 1, "READ_SIMS", 200, 120))
        assertEquals("INVALID_OPERATION", p.accept(1, "one", 1, "RUN_SHELL", 200, 120))
        assertEquals("SESSION_EXPIRED", p.accept(1, "one", 1, "READ_SIMS", 119, 120))
        assertNull(p.accept(1, "one", 1, "READ_SIMS", 200, 120))
        p.complete("one", 1, true)
        assertEquals("REPLAY_REJECTED", p.accept(1, "one", 1, "READ_SIMS", 220, 130))
        assertEquals("STALE_EPOCH", p.accept(1, "two", 0, "READ_SIMS", 220, 130))
    }
    @Test fun deathAndLeaseNeverKillRunningOperation() {
        val p = policy(); p.handshake("challenge", 110)
        p.accept(1, "one", 1, "APPLY_CONFIG", 200, 120)
        p.clientDied(150)
        assertFalse(p.mayShutdown(5000))
        p.complete("one", 1, true)
        assertTrue(p.mayShutdown(5000))
    }
    @Test fun failedCleanupBlocksShutdownAndFurtherRequests() {
        val p = policy(); p.handshake("challenge", 110)
        p.accept(1, "one", 1, "APPLY_CONFIG", 200, 120)
        p.complete("one", 1, false)
        assertFalse(p.mayShutdown(5000))
        assertEquals("CLEANUP_FAILED", p.accept(1, "two", 1, "READ_SIMS", 200, 130))
    }
    @Test fun failedAcquisitionNeverCleansSomeoneElsesDelegation() {
        val lease = DelegationLease()
        var cleanups = 0
        runCatching { lease.begin { error("DELEGATION_BUSY") } }
        assertTrue(lease.finish { cleanups++ })
        assertEquals(0, cleanups)
        lease.begin { }
        assertTrue(lease.finish { cleanups++ })
        assertTrue(lease.finish { cleanups++ })
        assertEquals(1, cleanups)
    }
    @Test fun failedOwnedCleanupRemainsUnknown() {
        val lease = DelegationLease(); lease.begin { }
        assertFalse(lease.finish { error("remote died") })
        assertFalse(lease.cleanupConfirmed)
    }
    @Test fun expiredSessionAllowsOwnedCleanupOnly() {
        val lifetime = SessionLifetime("one", 200)
        assertNull(lifetime.check("one", 201, cleanup = true))
        assertEquals("SESSION_EXPIRED", lifetime.check("one", 201, cleanup = false))
        assertEquals("AUTH_FAILED", lifetime.check("two", 100, cleanup = true))
        lifetime.close()
        assertEquals("AUTH_FAILED", lifetime.check("one", 100, cleanup = true))
    }
    @Test fun readOperationsRejectWriteActions() {
        assertFalse(OperationActionPolicy.valid("READ_PERSISTENT_VOLTE", "enable"))
        assertFalse(OperationActionPolicy.valid("READ_CAPTIVE_PORTAL", "write"))
        assertFalse(OperationActionPolicy.valid("SET_PERSISTENT_VOLTE", "restore"))
        assertTrue(OperationActionPolicy.valid("READ_PERSISTENT_VOLTE", "query"))
        assertTrue(OperationActionPolicy.valid("RESTORE_PERSISTENT_VOLTE", "restore_for_reset"))
    }
    @Test fun unconfirmedHandshakeHasBoundedIdleLease() {
        val p = policy(); assertTrue(p.handshake("challenge", 120))
        assertTrue(p.mayShutdown(1200))
    }
    @Test fun connectedLiveClientRetainsIdleService() {
        val p = policy(); p.handshake("challenge", 120); p.confirmClient()
        assertFalse(p.mayShutdown(1200))
    }

    @Test fun staleCompletionCannotReleaseANewerOperation() {
        val p = policy(); p.handshake("challenge", 110)
        p.accept(1, "one", 1, "APPLY_CONFIG", 200, 120)
        assertTrue(p.complete("one", 1, true))
        assertNull(p.accept(1, "two", 2, "APPLY_CONFIG", 300, 130))
        assertFalse(p.complete("one", 1, true))
        assertTrue(p.isActive)
    }

    @Test fun shutdownRevokesBeforeDelayedProcessExit() {
        val p = policy(); p.handshake("challenge", 110); p.confirmClient()
        assertTrue(p.requestShutdown())
        assertEquals("AUTH_FAILED", p.accept(1, "one", 1, "APPLY_CONFIG", 200, 120))
    }
    @Test fun expiredLeaseClaimRejectsConcurrentRequest() {
        val p = policy(); p.handshake("challenge", 110)
        assertTrue(p.claimExpiredShutdown(1200))
        assertEquals("AUTH_FAILED", p.accept(1, "one", 1, "READ_SIMS", 1300, 1201))
    }

    @Test fun failedRollbackAlwaysKeepsRecoveryRecord() {
        assertTrue(OperationRecoveryPolicy.required(rollbackFailed = true))
    }
    @Test fun interruptedRestorationKeepsRecoveryRecord() {
        assertTrue(OperationRecoveryPolicy.required(restorationStarted = true))
    }
    @Test fun failedCarrierReadbackKeepsRecoveryRecord() {
        assertTrue(OperationRecoveryPolicy.required(carrierWriteAttempted = true))
    }
    @Test fun confirmedPermissionDenialAllowsSameBackendFallback() {
        assertFalse(OperationRecoveryPolicy.required(carrierWriteAttempted = true, confirmedPermissionDenial = true))
        assertFalse(OperationRecoveryPolicy.required())
    }

    @Test fun deadConnectionCanBeReplacedWithoutStaleDeathRevokingNewChallenge() {
        val state = BootstrapLifecycle()
        val old = state.expect("old", connectionAlive = false)
        assertEquals(old, state.consume("old"))
        assertTrue(state.accept(old, "old"))
        val fresh = state.expect("fresh", connectionAlive = false)
        assertFalse(state.clear(old))
        assertEquals(fresh, state.consume("fresh"))
        assertTrue(state.accept(fresh, "fresh"))
        assertEquals("fresh", state.acceptedChallenge)
    }
    @Test fun liveConnectionCannotBeReplacedOrLateHandshakeAccepted() {
        val state = BootstrapLifecycle()
        val old = state.expect("old", connectionAlive = false)
        assertTrue(runCatching { state.expect("new", connectionAlive = true) }.isFailure)
        state.clear()
        assertFalse(state.accept(old, "old"))
        assertNull(state.acceptedChallenge)
    }

    @Test fun businessPreflightDoesNotRequireIdentityButOutgoingRequestDoes() {
        assertTrue(IdentityRequirementPolicy.valid(requireCaptured = false, simWrite = true, selectedSubId = 2, single = false, all = false))
        assertFalse(IdentityRequirementPolicy.valid(requireCaptured = true, simWrite = true, selectedSubId = 2, single = false, all = false))
        assertTrue(IdentityRequirementPolicy.valid(requireCaptured = true, simWrite = true, selectedSubId = 2, single = true, all = false))
        assertTrue(IdentityRequirementPolicy.valid(requireCaptured = true, simWrite = true, selectedSubId = -1, single = false, all = true))
    }
    @Test fun writePermissionDenialBeforeAnyAcceptedWriteCanFallback() {
        val state = CarrierWriteProgress(); state.beginWrite(); state.permissionRejected()
        assertTrue(state.retryAllowed); assertFalse(state.uncertainOnFailure)
    }
    @Test fun readPermissionFailureAfterAcceptedWriteCannotFallback() {
        val state = CarrierWriteProgress(); state.beginWrite(); state.writeAccepted()
        assertFalse(state.retryAllowed); assertTrue(state.uncertainOnFailure)
    }
    @Test fun secondCardPermissionFailureCannotReplayFirstCard() {
        val state = CarrierWriteProgress(); state.beginWrite(); state.writeAccepted()
        state.beginWrite(); state.permissionRejected()
        assertFalse(state.retryAllowed); assertTrue(state.uncertainOnFailure)
    }
    @Test fun unknownWriteTransportCannotFallback() {
        val state = CarrierWriteProgress(); state.beginWrite()
        assertFalse(state.retryAllowed); assertTrue(state.uncertainOnFailure)
    }

    @Test fun legacyBatchPartialFailureKeepsFirstCardAndBlocksReplay() {
        var now = 0L
        val writes = mutableListOf<Int>()
        val adapter = object : CarrierBatchAdapter {
            override fun identity(subId: Int) = "identity-$subId"
            override fun alreadyMatches(subId: Int) = false
            override fun write(subId: Int) { writes += subId; if (subId == 2) throw CarrierWritePermissionDenied(SecurityException()) }
            override fun verify(subId: Int, resetting: Boolean) = CarrierVerification.VERIFIED
        }
        val progress = CarrierWriteProgress()
        val result = CarrierBatchRunner({ now }, { now += it }).run(listOf(1, 2), mapOf(1 to "identity-1", 2 to "identity-2"), false, adapter, progress)
        assertEquals(setOf(1), result.verified); assertEquals(listOf(1, 2), writes)
        assertTrue(progress.uncertainOnFailure); assertFalse(progress.retryAllowed)
    }
    @Test fun silentLegacyWriteWithoutReadbackIsUnknown() {
        var now = 0L
        val adapter = object : CarrierBatchAdapter {
            override fun identity(subId: Int) = "one"
            override fun alreadyMatches(subId: Int) = false
            override fun write(subId: Int) = Unit
            override fun verify(subId: Int, resetting: Boolean) = CarrierVerification.NOT_YET
        }
        val progress = CarrierWriteProgress()
        val result = CarrierBatchRunner({ now }, { now += it }).run(listOf(1), mapOf(1 to "one"), false, adapter, progress)
        assertNotNull(result.failure); assertTrue(result.verified.isEmpty()); assertTrue(progress.uncertainOnFailure)
    }
    @Test fun mergedResetSnapshotCannotProveOverrideRemoval() {
        var now = 0L
        val adapter = object : CarrierBatchAdapter {
            override fun identity(subId: Int) = "one"
            override fun alreadyMatches(subId: Int) = true
            override fun write(subId: Int) = Unit
            override fun verify(subId: Int, resetting: Boolean) = CarrierVerification.UNPROVABLE
        }
        val progress = CarrierWriteProgress()
        val result = CarrierBatchRunner({ now }, { now += it }).run(listOf(1), mapOf(1 to "one"), true, adapter, progress)
        assertNotNull(result.failure); assertTrue(result.verified.isEmpty()); assertTrue(progress.uncertainOnFailure)
    }

    @Test fun finalPersistentReadbackFailureKeepsRecovery() {
        assertTrue(PersistentReadbackPolicy.requiresRecovery(writeStarted = true, criticalReadbackFailed = true))
        assertFalse(PersistentReadbackPolicy.requiresRecovery(writeStarted = false, criticalReadbackFailed = true))
    }
    @Test fun diagnosticOnlyFailureDoesNotInvalidatePersistentWrite() {
        assertFalse(PersistentReadbackPolicy.requiresRecovery(writeStarted = true, criticalReadbackFailed = false))
    }
    @Test fun originalBackupRequiresIdentityAndFinalValuesBeforeDeletion() {
        assertFalse(PersistentReadbackPolicy.mayClearBackup(identityMatches = true, originalMatches = false))
        assertFalse(PersistentReadbackPolicy.mayClearBackup(identityMatches = false, originalMatches = true))
        assertTrue(PersistentReadbackPolicy.mayClearBackup(identityMatches = true, originalMatches = true))
    }

}
