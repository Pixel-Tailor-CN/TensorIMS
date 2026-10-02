package app.mystery0.ims.tensor.privilege

import android.app.IActivityManager
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemClock
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.DelegationLease
import app.mystery0.ims.tensor.bridge.IPrivilegeSession
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import app.mystery0.ims.tensor.bridge.SessionLifetime
import app.mystery0.ims.tensor.privileged.stopDelegateShellPermissionIdentityCompat
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony

/** 会话只公开固定操作所需的能力；Binder 包装器仅由两个后端内部构造。 */
open class ExplicitPrivilegeSession internal constructor(
    private val context: Context,
    private val identity: InstalledIdentity,
    private val operation: OperationType,
    arguments: Bundle,
    private val operationId: String,
    private val epoch: Long,
    private val wrapService: (IBinder) -> IBinder,
) : IPrivilegeSession.Stub() {
    private val lease = DelegationLease()
    private val expiresAt = SystemClock.elapsedRealtime() + BridgeProtocol.REQUEST_MS
    private val lifetime = SessionLifetime(operationId, expiresAt)
    private val selectedSubId = OperationArguments.targetSubId(operation, arguments)
    private val targetSubIds = arguments.getBundle(BridgeProtocol.TARGET_IDENTITIES)?.keySet()?.map { it.toInt() }?.toSet().orEmpty()
    private val allowedSlots = HashSet<Int>()
    private val consumedSlots = HashSet<Int>()
    private var closed = false
    private var acquisitionUnknown = false
    private var delegationStarted = false
    var failureCode: String? = null
        private set
    private val manager: IActivityManager by lazy {
        IActivityManager.Stub.asInterface(wrapService(requireNotNull(ServiceManager.getService(Context.ACTIVITY_SERVICE))))
    }
    private val telephony: ITelephony by lazy {
        ITelephony.Stub.asInterface(wrapService(requireNotNull(ServiceManager.getService("phone"))))
    }
    private val subscription: ISub by lazy {
        ISub.Stub.asInterface(wrapService(requireNotNull(ServiceManager.getService("isub"))))
    }
    fun attach(arguments: Bundle): Bundle = Bundle(arguments).apply {
        putBinder(BridgeProtocol.SESSION, this@ExplicitPrivilegeSession)
        putString(BridgeProtocol.OPERATION_ID, operationId)
        putLong(BridgeProtocol.EPOCH, epoch)
        putInt(BridgeProtocol.PROTOCOL, BridgeProtocol.VERSION)
    }
    private fun authorize(id: String, cleanup: Boolean = false) {
        identity.requireCurrent(context, Binder.getCallingUid())
        val error = lifetime.check(id, SystemClock.elapsedRealtime(), cleanup)
        check(error == null) { requireNotNull(error) }
    }
    private fun <T> asBackend(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try { block() } finally { Binder.restoreCallingIdentity(token) }
    }
    @Synchronized override fun beginDelegation(id: String) {
        authorize(id)
        check(!delegationStarted) { "REPLAY_REJECTED" }
        try {
            asBackend { lease.begin { manager.startDelegateShellPermissionIdentity(identity.uid, permissions(operation)) } }
            delegationStarted = true
        } catch (failure: RemoteException) {
            // 传输中断不能证明系统没有建立委托；不猜测归属，也不擅自清理。
            acquisitionUnknown = true
            failureCode = "OPERATION_INDETERMINATE"
            throw failure
        } catch (failure: SecurityException) {
            if (failure.message.orEmpty().contains("one instrumentation", true)) {
                failureCode = "DELEGATION_BUSY"
                throw IllegalStateException("DELEGATION_BUSY")
            }
            throw failure
        }
    }
    @Synchronized override fun endDelegation(id: String) {
        authorize(id, cleanup = true)
        val clean = asBackend { lease.finish { manager.stopDelegateShellPermissionIdentityCompat(identity.uid) } }
        if (!clean) failureCode = "CLEANUP_FAILED"
        check(clean) { "CLEANUP_FAILED" }
    }
    @Synchronized fun finishAndConfirmCleanup(): Boolean {
        if (closed) return !acquisitionUnknown && lease.cleanupConfirmed
        val clean = !acquisitionUnknown && asBackend { lease.finish { manager.stopDelegateShellPermissionIdentityCompat(identity.uid) } }
        if (!clean) failureCode = "CLEANUP_FAILED"
        closed = true
        lifetime.close()
        return clean
    }
    private fun authorizeSubId(id: String, subId: Int) {
        authorize(id)
        check(delegationStarted && !lease.cleanupConfirmed) { "AUTH_FAILED: delegation inactive" }
        require(subId >= 0 && (if (selectedSubId == -1) subId in targetSubIds else subId == selectedSubId)) { "INVALID_TARGET" }
    }
    @Synchronized override fun getSlotIndex(id: String, subId: Int): Int {
        authorizeSubId(id, subId)
        check(operation == OperationType.RESET_IMS) { "INVALID_OPERATION" }
        val slot = asBackend { subscription.getSlotIndex(subId) }
        check(slot in 0..7) { "INVALID_TARGET" }
        check(slot !in consumedSlots) { "REPLAY_REJECTED" }
        allowedSlots += slot
        return slot
    }
    @Synchronized override fun resetIms(id: String, slotIndex: Int) {
        authorize(id)
        check(operation == OperationType.RESET_IMS && delegationStarted && !lease.cleanupConfirmed) { "INVALID_OPERATION" }
        require(allowedSlots.remove(slotIndex) && consumedSlots.add(slotIndex)) { "INVALID_TARGET" }
        try { asBackend { telephony.resetIms(slotIndex) } } catch (failure: Throwable) {
            failureCode = "OPERATION_INDETERMINATE"
            throw failure
        }
    }
    @Synchronized override fun isImsRegistered(id: String, subId: Int): Boolean {
        authorizeSubId(id, subId)
        check(operation in setOf(OperationType.READ_CAPABILITIES, OperationType.READ_PERSISTENT_VOLTE,
            OperationType.SET_PERSISTENT_VOLTE, OperationType.RESTORE_PERSISTENT_VOLTE)) { "INVALID_OPERATION" }
        return asBackend { telephony.isImsRegistered(subId) }
    }
    companion object {
        /** 精确权限集合；不再使用 null 申请 shell 全权限。真机矩阵仍须验证各版本行为。 */
        private fun permissions(operation: OperationType): Array<String> = when (operation) {
            OperationType.READ_CAPTIVE_PORTAL -> emptyArray()
            OperationType.WRITE_CAPTIVE_PORTAL, OperationType.RESET_CAPTIVE_PORTAL -> arrayOf("android.permission.WRITE_SECURE_SETTINGS")
            OperationType.READ_SIMS, OperationType.READ_CONFIG -> arrayOf("android.permission.READ_PHONE_STATE", "android.permission.READ_PRIVILEGED_PHONE_STATE")
            OperationType.READ_CAPABILITIES, OperationType.READ_PERSISTENT_VOLTE -> arrayOf("android.permission.READ_PHONE_STATE",
                "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.READ_PRECISE_PHONE_STATE")
            OperationType.SET_PERSISTENT_VOLTE, OperationType.RESTORE_PERSISTENT_VOLTE -> arrayOf("android.permission.READ_PHONE_STATE",
                "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.MODIFY_PHONE_STATE", "android.permission.READ_PRECISE_PHONE_STATE")
            else -> arrayOf("android.permission.READ_PHONE_STATE", "android.permission.READ_PRIVILEGED_PHONE_STATE", "android.permission.MODIFY_PHONE_STATE")
        }
    }
}
