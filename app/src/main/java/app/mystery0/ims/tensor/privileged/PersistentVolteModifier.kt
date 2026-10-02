package app.mystery0.ims.tensor.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.PersistentReadbackPolicy
import android.telephony.SubscriptionManager
import android.util.Log

class PersistentVolteModifier : SessionInstrumentation() {
    companion object {
        private const val TAG = "PersistentVolte"
        private val operationLock = Any()
        const val SUB_ID = "sub_id"
        const val ACTION = "action"
        const val QUERY = "query"
        const val ENABLE = "enable"
        const val RESTORE = "restore"
        const val RESTORE_FOR_RESET = "restore_for_reset"
        const val OPT_IN = "opt_in"
        const val USER_ENABLED = "user_enabled"
        const val IMS_REGISTERED = "ims_registered"
        const val CAN_RESTORE = "can_restore"
        const val ERROR = "error"
        const val UNSUPPORTED = "unsupported"
        const val COMPLETED = "completed"
    }

    private var arguments = Bundle()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        // 文件写入和系统服务调用放在 Instrumentation 工作线程，避免阻塞主界面。
        start()
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        val result = Bundle()
        val subId = arguments.getInt(SUB_ID, -1)
        val action = arguments.getString(ACTION)
        var writeStarted = false
        synchronized(operationLock) {
            val failure = runWithShellPermissionDelegation(TAG) {
                require(action in setOf(QUERY, ENABLE, RESTORE, RESTORE_FOR_RESET)) { "Invalid persistent VoLTE action" }
                if (subId == -1 && action == RESTORE_FOR_RESET) {
                    val subscriptions = targetContext.getSystemService(SubscriptionManager::class.java)
                        .activeSubscriptionInfoList ?: error("Cannot read active subscriptions")
                    val failures = mutableListOf<String>()
                    for (sim in subscriptions) {
                        try {
                            prepareRestore(sim.subscriptionId) { writeStarted = true }?.let {
                                finishRestore(sim.subscriptionId, it)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Restore failed for subId=${sim.subscriptionId}", t)
                            failures += "SIM ${sim.subscriptionId}: ${describeFailure(t)}"
                        }
                    }
                    check(failures.isEmpty()) { failures.joinToString("\n") }
                } else {
                    require(subId >= 0) { "Select a single active SIM" }
                    var operationFailure: Throwable? = null
                    var restoration: PersistentVolteBackup.Entry? = null
                    try {
                        when (action) {
                            ENABLE -> enable(subId) { writeStarted = true }
                            RESTORE, RESTORE_FOR_RESET -> restoration = prepareRestore(subId) { writeStarted = true }
                        }
                    } catch (t: Throwable) { operationFailure = t }
                    // 关键持久值与 IMS 诊断分离；写后关键回读失败必须保留备份与恢复日志。
                    val criticalReadback = if (action == RESTORE_FOR_RESET) true else readState(subId, result, action == QUERY)
                    if (PersistentReadbackPolicy.requiresRecovery(writeStarted, !criticalReadback)) {
                        result.putString(BridgeProtocol.ERROR_CODE, "OPERATION_INDETERMINATE")
                    }
                    if (operationFailure == null && criticalReadback) restoration?.let {
                        finishRestore(subId, it)
                        result.putBoolean(CAN_RESTORE, false)
                    }
                    operationFailure?.let { throw it }
                }
            }
            if (failure != null) {
                if (writeStarted || failure.suppressed.isNotEmpty()) result.putString(BridgeProtocol.ERROR_CODE, "OPERATION_INDETERMINATE")
                Log.e(TAG, "Persistent VoLTE operation failed", failure)
                result.putString(ERROR, describeFailure(failure))
                result.putBoolean(UNSUPPORTED, result.getBoolean(UNSUPPORTED) || isUnsupported(failure))
            }
            result.putBoolean(COMPLETED, failure == null && !result.containsKey(ERROR))
        }
        finish(Activity.RESULT_OK, result)
    }

    private fun enable(subId: Int, onWrite: () -> Unit) {
        val settings = PersistentVolteSettings(targetContext, subId)
        val identity = PersistentVolteBackup.identityDigest(settings.simIdentity())
        val backup = PersistentVolteBackup(targetContext, subId)
        val existing = backup.read()
        check(existing == null || existing.identity == identity) { "SIM identity does not match recovery record" }
        val before = settings.readOriginal()
        if (existing == null) backup.save(PersistentVolteBackup.Entry(identity, before))
        try {
            onWrite()
            settings.enable()
            check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == identity) { "SIM changed during operation" }
            Log.i(TAG, "Persistent VoLTE enabled for subId=$subId")
        } catch (t: Throwable) {
            try {
                check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == identity) { "SIM changed; rollback deferred" }
                settings.restore(before)
                // 即使即时回滚成功也保留原值记录，末次读取失败时不能丢失恢复依据。
                Log.i(TAG, "Persistent VoLTE rollback completed for subId=$subId")
            } catch (rollback: Throwable) {
                t.addSuppressed(rollback)
                Log.e(TAG, "Persistent VoLTE rollback failed for subId=$subId", rollback)
            }
            throw t
        }
    }

    private fun prepareRestore(subId: Int, onWrite: () -> Unit): PersistentVolteBackup.Entry? {
        val entry = PersistentVolteBackup(targetContext, subId).read() ?: return null
        val settings = PersistentVolteSettings(targetContext, subId)
        check(PersistentVolteBackup.identityDigest(settings.simIdentity()) == entry.identity) { "SIM identity does not match recovery record" }
        onWrite()
        settings.restore(entry.values)
        return entry
    }

    private fun finishRestore(subId: Int, entry: PersistentVolteBackup.Entry) {
        val settings = PersistentVolteSettings(targetContext, subId)
        val identityMatches = PersistentVolteBackup.identityDigest(settings.simIdentity()) == entry.identity
        val originalMatches = settings.readOriginal() == entry.values && settings.readOptIn() == (entry.values.optIn == 1)
        check(PersistentReadbackPolicy.mayClearBackup(identityMatches &&
            PersistentVolteBackup.identityDigest(settings.simIdentity()) == entry.identity, originalMatches)) { "Final original VoLTE verification failed; backup retained" }
        PersistentVolteBackup(targetContext, subId).clear()
        Log.i(TAG, "Original VoLTE settings verified and recovery record cleared for subId=$subId")
    }

    private fun readState(subId: Int, result: Bundle, diagnosticAsError: Boolean): Boolean {
        val criticalErrors = mutableListOf<String>()
        fun critical(block: () -> Unit) {
            try { block() } catch (t: Throwable) {
                Log.w(TAG, "Persistent VoLTE critical readback failed for subId=$subId", t)
                criticalErrors += describeFailure(t)
                if (isUnsupported(t)) result.putBoolean(UNSUPPORTED, true)
            }
        }
        critical { result.putBoolean(CAN_RESTORE, PersistentVolteBackup(targetContext, subId).read() != null) }
        critical {
            val settings = PersistentVolteSettings(targetContext, subId)
            settings.simIdentity()
            critical { result.putBoolean(OPT_IN, settings.readOptIn()) }
            critical { result.putBoolean(USER_ENABLED, settings.readUserEnabled()) }
        }
        try {
            result.putBoolean(IMS_REGISTERED, privilegeSession.isImsRegistered(operationId, subId))
        } catch (diagnostic: Throwable) {
            // 注册状态只是网络诊断，不推翻已经验证的持久值写入。
            val message = describeFailure(diagnostic)
            result.putString(BridgeProtocol.DIAGNOSTIC_WARNING, message)
            Log.w(TAG, "IMS registration diagnostic is unavailable", diagnostic)
            if (diagnosticAsError) result.putString(ERROR, message)
        }
        if (criticalErrors.isNotEmpty()) result.putString(ERROR, criticalErrors.joinToString("\n"))
        return criticalErrors.isEmpty()
    }

    private fun isUnsupported(t: Throwable): Boolean = when (t.privilegedRootCause()) {
        is ReflectiveOperationException, is LinkageError, is UnsupportedOperationException -> true
        else -> false
    }

    private fun describeFailure(t: Throwable): String = buildString {
        append(t.toPrivilegedErrorMessage())
        t.suppressed.forEach { append("\nRecovery: ").append(it.toPrivilegedErrorMessage()) }
    }
}
