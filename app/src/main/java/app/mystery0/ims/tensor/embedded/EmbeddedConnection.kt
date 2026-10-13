package app.mystery0.ims.tensor.embedded

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.BootstrapLifecycle
import app.mystery0.ims.tensor.bridge.ComputerBootstrapIdentity
import app.mystery0.ims.tensor.bridge.ComputerBootstrapLease
import app.mystery0.ims.tensor.bridge.IEmbeddedServer
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只保存本次显式启动挑战认证过的私有 Binder，与外置静态 Binder 完全隔离。 */
object EmbeddedConnection {
    private val lock = Any()
    private val mutableChanges = MutableStateFlow(0L)
    val changes: StateFlow<Long> = mutableChanges.asStateFlow()
    private val lifecycle = BootstrapLifecycle()
    private val computerLease = ComputerBootstrapLease()
    private var computerGeneration = -1L
    private var computerIdentityDigest: String? = null
    private var expectedUntil = 0L
    private var server: IEmbeddedServer? = null
    private var metadata: Bundle? = null
    private val clientToken = Binder()
    private var death: IBinder.DeathRecipient? = null
    fun current(): IEmbeddedServer? = synchronized(lock) { server?.takeIf { it.asBinder().isBinderAlive } }
    fun acceptedChallenge(): String? = synchronized(lock) { lifecycle.acceptedChallenge }
    fun expect(challenge: String) = synchronized(lock) {
        require(challenge.matches(Regex("[a-f0-9]{64}")))
        revokeComputerLease()
        lifecycle.expect(challenge, server?.asBinder()?.isBinderAlive == true)
        // 已死亡连接先完整摘除，旧死亡回调必须按代际过滤，再登记新挑战。
        death?.let { runCatching { server?.asBinder()?.unlinkToDeath(it, 0) } }
        server = null; death = null; metadata = null
        expectedUntil = SystemClock.elapsedRealtime() + BridgeProtocol.REQUEST_MS
    }
    fun expectComputer(challenge: String, identityDigest: String, epoch: Long, timeoutMs: Long): Long = synchronized(lock) {
        require(challenge.matches(Regex("[a-f0-9]{64}")) && identityDigest.matches(Regex("[a-f0-9]{64}")))
        val status = PrivilegeRuntime.status.value
        check(status.mode == BackendMode.EMBEDDED && status.epoch == epoch) { "MODE_NOT_EMBEDDED" }
        check(server?.asBinder()?.isBinderAlive != true) { "SERVER_ALREADY_CONNECTED" }
        revokeComputerLease()
        val generation = lifecycle.expect(challenge, false)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        expectedUntil = deadline
        computerGeneration = generation
        computerIdentityDigest = identityDigest
        computerLease.prepare(challenge, generation, epoch, identityDigest, deadline)
        generation
    }

    internal fun claimComputer(context: Context, callerUid: Int): Bundle = synchronized(lock) {
        require(callerUid == 0 || callerUid == 2000) { "AUTH_FAILED: bootstrap UID" }
        val status = PrivilegeRuntime.status.value
        check(status.mode == BackendMode.EMBEDDED && status.epoch >= 0) { "MODE_NOT_EMBEDDED" }
        check(server == null && SystemClock.elapsedRealtime() < expectedUntil) { "AUTH_FAILED: no computer wait" }
        check(context.packageName == BridgeProtocol.PACKAGE) { "AUTH_FAILED: provider package" }
        val identity = InstalledIdentity.read(context)
        val libraryDir = requireNotNull(context.applicationInfo).nativeLibraryDir
        val digest = ComputerBootstrapIdentity.digest(identity, java.io.File(libraryDir).canonicalPath)
        check(digest == computerIdentityDigest && lifecycle.isCurrent(computerGeneration)) {
            "AUTH_FAILED: computer installation changed"
        }
        val challenge = computerLease.claim(SystemClock.elapsedRealtime(), status.epoch, digest, callerUid)
            ?: error("AUTH_FAILED: computer claim unavailable")
        Bundle().apply {
            putBoolean("accepted", true)
            putString(BridgeProtocol.CHALLENGE, challenge)
            putLong(BridgeProtocol.COMPUTER_GENERATION, computerGeneration)
            putLong(BridgeProtocol.EPOCH, status.epoch)
            putString(BridgeProtocol.COMPUTER_IDENTITY, digest)
            putInt(BridgeProtocol.RUNTIME_UID, callerUid)
        }
    }
    fun clear() = synchronized(lock) {
        lifecycle.cancelPending()
        revokeComputerLease()
        val existing = server
        if (existing != null && existing.asBinder().isBinderAlive) {
            // 取消启动也必须撤销已认证空闲实例；服务端拒绝时保留引用，不能遗忘运行中操作。
            val instance = metadata?.getString(BridgeProtocol.INSTANCE_ID)
            val stopped = instance != null && runCatching { existing.shutdownOwnedServer(instance) }.getOrDefault(false)
            if (!stopped && existing.asBinder().isBinderAlive) { mutableChanges.value++; return@synchronized }
        }
        death?.let { runCatching { server?.asBinder()?.unlinkToDeath(it, 0) } }
        server = null; death = null; metadata = null
        lifecycle.clear()
        mutableChanges.value++
    }
    internal fun accept(context: Context, callerUid: Int, challenge: String, binder: IBinder): Boolean = synchronized(lock) {
        check(computerGeneration < 0) { "AUTH_FAILED: computer startup requires its lease" }
        acceptLocked(context, callerUid, challenge, binder)
    }

    internal fun acceptComputer(
        context: Context,
        callerUid: Int,
        challenge: String,
        generation: Long,
        epoch: Long,
        identityDigest: String,
        binder: IBinder,
    ): Boolean = synchronized(lock) {
        require(callerUid == 0 || callerUid == 2000) { "AUTH_FAILED: bootstrap caller" }
        val status = PrivilegeRuntime.status.value
        check(status.mode == BackendMode.EMBEDDED && status.epoch == epoch) { "AUTH_FAILED: stale backend epoch" }
        val identity = InstalledIdentity.read(context)
        val libraryDir = requireNotNull(context.applicationInfo).nativeLibraryDir
        val currentDigest = ComputerBootstrapIdentity.digest(identity, java.io.File(libraryDir).canonicalPath)
        check(currentDigest == identityDigest && currentDigest == computerIdentityDigest) {
            "AUTH_FAILED: computer installation changed"
        }
        check(generation == computerGeneration && lifecycle.isCurrent(generation)) { "AUTH_FAILED: stale computer generation" }
        check(computerLease.permitsDelivery(
            challenge,
            generation,
            SystemClock.elapsedRealtime(),
            epoch,
            identityDigest,
            callerUid,
        )) { "AUTH_FAILED: computer lease expired or unclaimed" }
        val accepted = acceptLocked(context, callerUid, challenge, binder)
        revokeComputerLease()
        accepted
    }

    private fun acceptLocked(context: Context, callerUid: Int, challenge: String, binder: IBinder): Boolean {
        require(callerUid == 0 || callerUid == 2000) { "AUTH_FAILED: bootstrap caller" }
        check(PrivilegeRuntime.status.value.mode == BackendMode.EMBEDDED) { "MODE_NOT_EMBEDDED" }
        check(SystemClock.elapsedRealtime() <= expectedUntil && server == null) { "AUTH_FAILED: unexpected bootstrap" }
        // 挑战先消费，失败也不允许同一启动消息重放。
        val generation = lifecycle.consume(challenge) ?: error("AUTH_FAILED: unexpected bootstrap")
        val candidate = IEmbeddedServer.Stub.asInterface(binder)
        val reply = candidate.handshake(challenge, clientToken)
        val identity = InstalledIdentity.read(context)
        check(reply.getInt(BridgeProtocol.PROTOCOL) == BridgeProtocol.VERSION) { "VERSION_MISMATCH" }
        check(reply.getString(BridgeProtocol.CHALLENGE) == challenge &&
            reply.getInt(BridgeProtocol.RUNTIME_UID, -1) == callerUid &&
            reply.getLong(BridgeProtocol.VERSION_CODE) == identity.versionCode &&
            reply.getString(BridgeProtocol.SIGNER) == identity.signer &&
            reply.getString(BridgeProtocol.APK_PATH) == identity.apkPath &&
            !reply.getString(BridgeProtocol.INSTANCE_ID).isNullOrBlank()) { "AUTH_FAILED: server identity" }
        check(reply.getStringArrayList(BridgeProtocol.CAPABILITIES)?.toSet()?.containsAll(app.mystery0.ims.tensor.bridge.BridgePolicy.OPERATIONS) == true) { "UNSUPPORTED_CAPABILITY" }
        val recipient = IBinder.DeathRecipient {
            synchronized(lock) { if (lifecycle.isCurrent(generation) && server?.asBinder() == binder) clear() }
        }
        binder.linkToDeath(recipient, 0)
        check(binder.isBinderAlive) { "Private server died during handshake" }
        check(lifecycle.accept(generation, challenge)) { "AUTH_FAILED: stale attempt" }
        server = candidate; death = recipient; metadata = Bundle(reply)
        mutableChanges.value++
        return true
    }

    private fun revokeComputerLease() {
        if (computerGeneration >= 0) computerLease.revoke(computerGeneration)
        computerGeneration = -1L
        computerIdentityDigest = null
    }
}
