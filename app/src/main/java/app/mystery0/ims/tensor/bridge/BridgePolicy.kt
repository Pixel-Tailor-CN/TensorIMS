package app.mystery0.ims.tensor.bridge

/** 无 Android 依赖的授权/租约状态；调用者须在同一服务锁内使用。 */
class BridgePolicy(
    private val ownerUid: Int,
    private val userId: Int,
    private val signer: String,
    private val challenge: String,
    private val createdAt: Long,
    private val leaseMs: Long,
) {
    private var authenticated = false
    private var confirmed = false
    private var closing = false
    private var activeId: String? = null
    private var deadAt: Long? = null
    private var active = false
    private var uncertainCleanup = false
    private var epoch = -1L
    private val seen = HashSet<String>()
    fun authenticate(uid: Int, user: Int, installedSigner: String): Boolean =
        uid == ownerUid && user == userId && installedSigner == signer
    fun handshake(value: String, now: Long): Boolean {
        if (authenticated || value != challenge || now < createdAt || now - createdAt > leaseMs) return false
        authenticated = true
        return true
    }
    fun accept(version: Int, id: String, generation: Long, operation: String, expiresAt: Long, now: Long): String? {
        if (!authenticated || deadAt != null || closing) return "AUTH_FAILED"
        if (uncertainCleanup) return "CLEANUP_FAILED"
        if (version != 1) return "VERSION_MISMATCH"
        if (operation !in OPERATIONS) return "INVALID_OPERATION"
        if (expiresAt <= now || expiresAt - now > MAX_REQUEST_MS) return "SESSION_EXPIRED"
        if (!id.matches(Regex("[A-Za-z0-9-]{1,80}"))) return "INVALID_OPERATION_ID"
        if (id in seen || seen.size >= 4096) return "REPLAY_REJECTED"
        if (generation < epoch || generation < 0) return "STALE_EPOCH"
        if (active) return "OPERATION_BUSY"
        epoch = generation
        seen += id
        activeId = id
        confirmed = true
        active = true
        return null
    }
    fun confirmClient() { check(authenticated && deadAt == null && !closing); confirmed = true }
    fun complete(id: String, generation: Long, cleanupConfirmed: Boolean): Boolean {
        if (id != activeId || generation != epoch) return false
        active = false; uncertainCleanup = !cleanupConfirmed
        return true
    }
    fun clientDied(now: Long) { deadAt = now }
    fun mayShutdown(now: Long): Boolean = !active && !uncertainCleanup &&
        ((!confirmed && now - createdAt >= leaseMs) || deadAt?.let { now - it >= leaseMs } == true)
    fun idleOwned(): Boolean = !closing && authenticated && deadAt == null && !active && !uncertainCleanup
    fun requestShutdown(): Boolean {
        if (!idleOwned()) return false
        closing = true
        return true
    }
    fun claimExpiredShutdown(now: Long): Boolean {
        if (closing || !mayShutdown(now)) return false
        closing = true
        return true
    }
    val isActive: Boolean get() = active
    val cleanupFailed: Boolean get() = uncertainCleanup
    companion object {
        const val MAX_REQUEST_MS = 120_000L
        val OPERATIONS = setOf("READ_SIMS", "READ_CAPABILITIES", "READ_CONFIG", "APPLY_CONFIG", "BROKER_CONFIG",
            "RESET_IMS", "READ_PERSISTENT_VOLTE", "SET_PERSISTENT_VOLTE", "RESTORE_PERSISTENT_VOLTE",
            "READ_CAPTIVE_PORTAL", "WRITE_CAPTIVE_PORTAL", "RESET_CAPTIVE_PORTAL")
    }
}

/** acquire 失败绝不调用 release；清理失败后保留未知状态，禁止自动重试旧版无 UID 清理。 */
class DelegationLease {
    private var acquired = false
    private var failed = false
    val cleanupConfirmed: Boolean get() = !acquired && !failed
    fun begin(acquire: () -> Unit) {
        check(!acquired && !failed) { "DELEGATION_BUSY" }
        acquire()
        acquired = true
    }
    fun finish(release: () -> Unit): Boolean {
        if (failed) return false
        if (!acquired) return true
        return try { release(); acquired = false; true } catch (_: Throwable) { failed = true; false }
    }
}

/** 业务方法受期限限制，已获得委托的清理只受操作 ID 与撤销状态限制。 */
class SessionLifetime(private val operationId: String, private val expiresAt: Long) {
    private var closed = false
    fun check(id: String, now: Long, cleanup: Boolean): String? = when {
        closed || id != operationId -> "AUTH_FAILED"
        !cleanup && now > expiresAt -> "SESSION_EXPIRED"
        else -> null
    }
    fun close() { closed = true }
}

/** 与 Android Bundle 无关的 action 白名单，防止只读操作偷渡写入。 */
object OperationActionPolicy {
    fun valid(operation: String, action: String?): Boolean = when (operation) {
        "READ_PERSISTENT_VOLTE" -> action == "query"
        "SET_PERSISTENT_VOLTE" -> action == "enable"
        "RESTORE_PERSISTENT_VOLTE" -> action == "restore" || action == "restore_for_reset"
        "READ_CAPTIVE_PORTAL" -> action == "read"
        "WRITE_CAPTIVE_PORTAL" -> action == "write"
        "RESET_CAPTIVE_PORTAL" -> action == "reset"
        else -> false
    }
}

/** 业务失败时保留不确定写入的恢复记录；明确权限拒绝仍可走同后端 Broker。 */
object OperationRecoveryPolicy {
    fun required(rollbackFailed: Boolean = false, restorationStarted: Boolean = false,
                 carrierWriteAttempted: Boolean = false, confirmedPermissionDenial: Boolean = false): Boolean =
        rollbackFailed || restorationStarted || (carrierWriteAttempted && !confirmedPermissionDenial)
}

/** 启动代际独立于 Binder 对象，旧死亡通知和迟到握手不得撤销新挑战。 */
class BootstrapLifecycle {
    private var generation = 0L
    private var expected: String? = null
    private var consumed: String? = null
    var acceptedChallenge: String? = null
        private set
    fun expect(challenge: String, connectionAlive: Boolean): Long {
        check(!connectionAlive) { "An authenticated private server is already connected" }
        generation++
        expected = challenge; consumed = null; acceptedChallenge = null
        return generation
    }
    fun consume(challenge: String): Long? {
        if (expected != challenge) return null
        expected = null; consumed = challenge
        return generation
    }
    fun accept(attempt: Long, challenge: String): Boolean {
        if (generation != attempt || consumed != challenge) return false
        consumed = null; acceptedChallenge = challenge
        return true
    }
    fun cancelPending() { expected = null; consumed = null }
    fun clear(attempt: Long? = null): Boolean {
        if (attempt != null && attempt != generation) return false
        generation++; cancelPending(); acceptedChallenge = null
        return true
    }
    fun isCurrent(attempt: Long) = generation == attempt
}

/** 捕获身份前后使用同一业务白名单，只有外发阶段强制要求完整身份。 */
object IdentityRequirementPolicy {
    fun valid(requireCaptured: Boolean, simWrite: Boolean, selectedSubId: Int, single: Boolean, all: Boolean): Boolean =
        !requireCaptured || !simWrite || if (selectedSubId >= 0) single else all
}

/** 只在实际写调用明确拒绝且此前没有已接受/未知写入时允许同后端重试。 */
class CarrierWriteProgress {
    private var acceptedWrites = 0
    private var dispatchPending = false
    private var permissionDenied = false
    fun beginWrite() { check(!dispatchPending); dispatchPending = true; permissionDenied = false }
    fun writeAccepted() { check(dispatchPending); dispatchPending = false; acceptedWrites++ }
    fun permissionRejected() { check(dispatchPending); dispatchPending = false; permissionDenied = true }
    val retryAllowed: Boolean get() = permissionDenied && acceptedWrites == 0 && !dispatchPending
    val uncertainOnFailure: Boolean get() = acceptedWrites > 0 || dispatchPending
}

class CarrierWritePermissionDenied(cause: Throwable) : Exception("Carrier configuration write permission denied", cause)
enum class CarrierVerification { VERIFIED, NOT_YET, UNPROVABLE }
interface CarrierBatchAdapter {
    fun identity(subId: Int): String
    fun alreadyMatches(subId: Int): Boolean
    fun write(subId: Int)
    fun verify(subId: Int, resetting: Boolean): CarrierVerification
}
data class CarrierBatchOutcome(val verified: Set<Int>, val failure: Throwable?)

/** 纯执行器把每卡身份、真实写事务和有界回读串联；部分成功后立即停止，不重放整批。 */
class CarrierBatchRunner(private val now: () -> Long, private val pause: (Long) -> Unit) {
    fun run(targets: List<Int>, identities: Map<Int, String>, reset: Boolean,
            adapter: CarrierBatchAdapter, progress: CarrierWriteProgress): CarrierBatchOutcome {
        val verified = linkedSetOf<Int>()
        return try {
            for (subId in targets) {
                val expectedIdentity = requireNotNull(identities[subId])
                check(adapter.identity(subId) == expectedIdentity) { "SIM identity changed before operation" }
                if (!reset && adapter.alreadyMatches(subId)) {
                    check(adapter.identity(subId) == expectedIdentity) { "SIM identity changed after initial read" }
                    verified += subId; continue
                }
                check(adapter.identity(subId) == expectedIdentity) { "SIM identity changed before writing" }
                progress.beginWrite()
                try { adapter.write(subId) } catch (denied: CarrierWritePermissionDenied) {
                    progress.permissionRejected(); throw denied
                }
                progress.writeAccepted()
                val deadline = now() + 4000L
                while (true) {
                    check(adapter.identity(subId) == expectedIdentity) { "SIM identity changed during verification" }
                    val state = adapter.verify(subId, reset)
                    check(adapter.identity(subId) == expectedIdentity) { "SIM identity changed after verification" }
                    if (state == CarrierVerification.VERIFIED) { verified += subId; break }
                    check(state != CarrierVerification.UNPROVABLE) {
                        "Reset request returned and current configuration was read; override removal cannot be independently verified"
                    }
                    check(now() < deadline) { "Carrier configuration read-back did not match" }
                    pause(150)
                }
            }
            CarrierBatchOutcome(verified, null)
        } catch (failure: Throwable) { CarrierBatchOutcome(verified, failure) }
    }
}

/** 关键持久值决定恢复记录；仅 IMS 注册诊断失败不等价于持久写入未知。 */
object PersistentReadbackPolicy {
    fun requiresRecovery(writeStarted: Boolean, criticalReadbackFailed: Boolean) = writeStarted && criticalReadbackFailed
    fun mayClearBackup(identityMatches: Boolean, originalMatches: Boolean) = identityMatches && originalMatches
}
