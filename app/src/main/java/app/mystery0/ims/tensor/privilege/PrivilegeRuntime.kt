package app.mystery0.ims.tensor.privilege

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.util.Log
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.embedded.EmbeddedConnection
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.privileged.CaptivePortalSettingsModifier
import app.mystery0.ims.tensor.privileged.ImsCapabilityReader
import app.mystery0.ims.tensor.privileged.ImsModifier
import app.mystery0.ims.tensor.privileged.PersistentVolteModifier
import app.mystery0.ims.tensor.privileged.SimReader
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 只按用户选择路由；未知结果保留后台任务和持久化安全门，绝不自动重放写入。 */
object PrivilegeRuntime {
    private const val TAG = "PrivilegeRuntime"
    private const val TIMEOUT_MS = 15_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val official = OfficialBackend()
    private val embedded = EmbeddedBackend()
    private lateinit var context: Context
    private lateinit var journal: OperationJournal
    private var machine = BackendStateMachine()
    private val _status = MutableStateFlow(machine.status)
    val status: StateFlow<BackendStatus> = _status.asStateFlow()
    private val _canRecoverPersistentVolte = MutableStateFlow(false)
    val canRecoverPersistentVolte: StateFlow<Boolean> = _canRecoverPersistentVolte.asStateFlow()
    private val _canStartEmbeddedForRecovery = MutableStateFlow(false)
    val canStartEmbeddedForRecovery: StateFlow<Boolean> = _canStartEmbeddedForRecovery.asStateFlow()
    private val _canRequestOfficialPermissionForRecovery = MutableStateFlow(false)
    val canRequestOfficialPermissionForRecovery: StateFlow<Boolean> = _canRequestOfficialPermissionForRecovery.asStateFlow()
    @Volatile private var recoveryInFlight = false
    @Volatile private var recoveryBackupPresent = false
    private val journalGuard = Any()
    @Volatile private var pending: OperationRecord? = null
    @Volatile private var corruptJournal = false
    @Volatile private var initialized = false

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        this.context = context.applicationContext
        journal = OperationJournal(File(this.context.noBackupFilesDir, "privilege_operations"))
        val preferences = preferences()
        val mode = runCatching { BackendMode.valueOf(preferences.getString("chosen_mode", "UNSET")!!) }
            .getOrDefault(BackendMode.UNSET)
        pending = try { journal.read() } catch (failure: Exception) {
            corruptJournal = true
            Log.e(TAG, "Operation journal could not be read", failure)
            null
        }
        machine = BackendStateMachine(mode, pending != null || corruptJournal)
        if (pending != null || corruptJournal) preferences.edit().putBoolean("restore_paused", true).commit()
        initialized = true
        publish()
        official.observe { if (status.value.mode == BackendMode.OFFICIAL) refresh() }
        scope.launch {
            EmbeddedConnection.changes.collect { if (status.value.mode == BackendMode.EMBEDDED) refresh() }
        }
        refresh()
    }

    suspend fun chooseMode(mode: BackendMode): String? {
        if (!initialized) return "后端尚未初始化"
        var previous: BackendStatus? = null
        return try { chooseModeLocked(mode) { previous = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            previous?.let { machine.abortSwitch(it, "SWITCH_FAILED", "切换未完成，请刷新当前后端后重试") }
            publish()
            Log.e(TAG, "Backend switch failed", failure)
            "切换未完成，已保留原模式，请刷新后重试"
        }
    }

    private suspend fun chooseModeLocked(mode: BackendMode, rememberPrevious: (BackendStatus) -> Unit): String? {
        var entered = false
        val error = OperationCoordinator.tryExclusive {
            entered = true
            if (machine.status.connection == ConnectionState.RECOVERY_REQUIRED || machine.activeOperationId != null) {
                return@tryExclusive "操作尚未结束或需要恢复，请完成核对后再切换"
            }
            if (mode == machine.status.mode) { connectLocked(); return@tryExclusive null }
            val old = machine.freezeForSwitch() ?: return@tryExclusive "当前操作尚未结束，请稍后再切换"
            rememberPrevious(old)
            publish()
            if (old.mode != BackendMode.UNSET && !backend(old.mode).disconnect()) {
                machine.abortSwitch(old, "SHUTDOWN_UNCONFIRMED", "旧后端尚未确认安全关闭，请稍后重试")
                publish()
                return@tryExclusive "旧后端尚未确认安全关闭，未切换模式"
            }
            if (!preferences().edit().putInt("schema", 1).putString("chosen_mode", mode.name).commit()) {
                machine.abortSwitch(old, "PREFERENCE_FAILED", "无法保存模式选择")
                publish()
                return@tryExclusive "无法保存模式选择"
            }
            if (!machine.completeSwitch(mode)) return@tryExclusive "后端状态已变化，请刷新后重试"
            publish()
            connectLocked()
            null
        }
        return if (entered) error else "当前操作尚未完成，请稍后再切换"
    }

    fun refresh() {
        if (!initialized) return
        scope.launch {
            try {
                OperationCoordinator.tryExclusive {
                    if (machine.status.connection == ConnectionState.RECOVERY_REQUIRED) reconcileLocked()
                    else if (machine.activeOperationId == null) connectLocked()
                }
            } catch (failure: Exception) {
                Log.e(TAG, "Backend refresh remains blocked", failure)
                publish()
            }
        }
    }

    fun requestOfficialPermission() {
        if (!initialized) return
        scope.launch {
            OperationCoordinator.tryExclusive {
                val value = machine.status
                if (value.mode != BackendMode.OFFICIAL || value.connection in setOf(
                        ConnectionState.BUSY, ConnectionState.SWITCHING,
                    ) || (value.connection == ConnectionState.RECOVERY_REQUIRED && !_canRequestOfficialPermissionForRecovery.value)) return@tryExclusive
                try { official.requestPermission() } catch (failure: Throwable) {
                    Log.w(TAG, "Official permission request failed", failure)
                    connectLocked()
                }
            }
        }
    }

    fun canRequestOfficialPermissionAutomatically(): Boolean = initialized &&
        status.value.mode == BackendMode.OFFICIAL && status.value.errorCode == "PERMISSION_REQUIRED" &&
        official.canRequestAutomatically()

    fun canAutoRestore(epoch: Long): Boolean = initialized && status.value.epoch == epoch &&
        status.value.isReady && !preferences().getBoolean("restore_paused", false)

    /** 用户核对后重新启用自动恢复，才允许解除未知结果造成的持久暂停。 */
    fun acknowledgeAutomaticRestore() {
        if (initialized && status.value.connection != ConnectionState.RECOVERY_REQUIRED) {
            preferences().edit().putBoolean("restore_paused", false).commit()
        }
    }

    suspend fun startEmbedded(action: suspend () -> String?): String? {
        if (!initialized) return "后端尚未初始化"
        var entered = false
        val result = OperationCoordinator.tryExclusive {
            entered = true
            if (machine.status.mode != BackendMode.EMBEDDED) return@tryExclusive "请先选择内置模式"
            val recovering = machine.status.connection == ConnectionState.RECOVERY_REQUIRED
            if (machine.activeOperationId != null || (recovering && !_canStartEmbeddedForRecovery.value)) {
                return@tryExclusive "旧操作需要恢复，不能启动新服务"
            }
            if (!recovering && !machine.beginConnection()) return@tryExclusive "当前操作尚未完成，请稍后再启动"
            publish()
            val error = try { action() } catch (failure: Exception) {
                Log.e(TAG, "Embedded start failed", failure)
                "内置服务启动失败，请检查启动方式"
            }
            if (recovering) reconcileLocked() else connectLocked()
            error
        }
        return if (entered) result else "当前操作尚未完成，请稍后再启动"
    }

    suspend fun execute(
        context: Context, operation: OperationType, args: Bundle?, canStart: () -> Boolean = { true },
    ): Bundle? {
        initialize(context)
        val requested = status.value
        return OperationCoordinator.serialized {
            if (!canStart() || requested.epoch != machine.status.epoch || requested.mode != machine.status.mode) {
                return@serialized errorResult(args, "请求已取消或模式已经变化", "REQUEST_CANCELLED")
            }
            if (!machine.status.isReady || machine.status.mode == BackendMode.UNSET) {
                return@serialized errorResult(args, machine.status.message ?: "请先选择并连接特权后端", machine.status.errorCode ?: "NOT_READY")
            }
            val arguments = try {
                OperationArguments.preflight(operation, args?.let(::Bundle) ?: Bundle())
            } catch (failure: IllegalArgumentException) {
                return@serialized errorResult(args, "操作参数无效，未执行", "INVALID_ARGUMENTS")
            }
            val id = UUID.randomUUID().toString()
            val epoch = machine.status.epoch
            if (!machine.begin(id)) return@serialized errorResult(args, "已有操作正在执行", "BUSY")
            val subId = subId(arguments)
            val record = OperationRecord(id, machine.status.mode, operation.name, epoch, bootCount(), subId,
                identityDigest = arguments.getString(TargetConfigProtocol.IDENTITY),
                recoveryReference = if (operation in persistentWrites && subId >= 0) "persistent_volte_$subId.json" else null)
            try { savePending(record) } catch (failure: Exception) {
                // 已知尚未派发，保留可核对的内存记录；不重置 epoch，也不把磁盘故障当成执行成功。
                machine.uncertain(id, epoch)
                machine.finish(id, epoch, true)
                synchronized(journalGuard) { pending = record.copy(phase = OperationPhase.TERMINAL, cleanupConfirmed = true) }
                preferences().edit().putBoolean("restore_paused", true).commit()
                publish()
                return@serialized errorResult(arguments, "无法可靠保存操作记录，已阻止执行", "JOURNAL_FAILED")
            }
            publish()
            val selected = backend(record.mode)
            val task = scope.async {
                try { Outcome(perform(selected, operation, arguments, record, canStart)) }
                catch (failure: Throwable) { Outcome(failure = failure) }
            }
            try {
                val outcome = withTimeoutOrNull(TIMEOUT_MS) { task.await() }
                if (outcome != null) finishLocked(record, outcome, arguments)
                else {
                    markUnknown(record)
                    awaitLate(task, record, arguments)
                    errorResult(arguments, "操作结果尚未确认；不会自动重试，请稍后刷新核对", "OPERATION_INDETERMINATE")
                }
            } catch (cancelled: CancellationException) {
                markUnknown(record)
                awaitLate(task, record, arguments)
                throw cancelled
            }
        }
    }

    private suspend fun perform(
        selected: PrivilegeBackend, operation: OperationType, arguments: Bundle,
        record: OperationRecord, canStart: () -> Boolean,
    ): Bundle? {
        if (operation.isWrite && operation !in captiveWrites) {
            val identities = captureIdentities(selected, record.subId, record.operationId, record.epoch)
            record.identityDigest?.let { expected ->
                if (identities[record.subId] != expected) throw OperationNotStartedException("SIM identity changed before execution")
            }
            updatePending { it.copy(identityDigest = identities[record.subId], targetIdentities = identities) }
            if (record.subId >= 0) arguments.putString(BridgeProtocol.TARGET_IDENTITY, identities[record.subId])
            else arguments.putBundle(BridgeProtocol.TARGET_IDENTITIES, Bundle().apply {
                identities.forEach { (id, digest) -> putString(id.toString(), digest) }
            })
        }
        if (!canStart() || !machine.mayContinue(record.operationId, record.epoch)) {
            throw OperationNotStartedException("Request cancelled or timed out before execution")
        }
        updatePending { it.copy(dispatched = true) }
        // 落盘本身也可能耗时，真正派发前再次检查取消与超时，不延迟启动已撤回的写入。
        if (!canStart() || !machine.mayContinue(record.operationId, record.epoch)) {
            updatePending { it.copy(dispatched = false) }
            throw OperationNotStartedException("Request cancelled or timed out before dispatch")
        }
        var result = selected.execute(context, operation, arguments, record.operationId, record.epoch)
        requireClean(result)
        if (operation.isWrite && result == null) {
            return errorResult(arguments, "写入终态缺少业务结果，请先核对当前系统状态", "OPERATION_INDETERMINATE")
                .apply { putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, true) }
        }
        if (operation == OperationType.APPLY_CONFIG && canStart() &&
            machine.mayContinue(record.operationId, record.epoch) && shouldFallback(result)) {
            result = selected.execute(context, OperationType.BROKER_CONFIG, Bundle(arguments), "${record.operationId}-fallback", record.epoch)
            requireClean(result)
            if (result == null) {
                return errorResult(arguments, "Broker 写入终态缺少业务结果，请先核对", "OPERATION_INDETERMINATE")
                    .apply { putBoolean(BridgeProtocol.CLEANUP_CONFIRMED, true) }
            }
        }
        return result
    }

    private suspend fun captureIdentities(selected: PrivilegeBackend, subId: Int, id: String, epoch: Long): Map<Int, String> {
        val ids = if (subId >= 0) listOf(subId) else {
            val sims = selected.execute(context, OperationType.READ_SIMS, Bundle(), "$id-sims", epoch)
            requireClean(sims)
            sims?.getParcelableArrayList(SimReader.BUNDLE_RESULT, SubscriptionInfo::class.java)
                ?.map { it.subscriptionId }.orEmpty()
        }
        if (ids.isEmpty()) throw OperationNotStartedException("No active SIM available for identity verification")
        return ids.associateWith { target ->
            val result = selected.execute(context, OperationType.READ_CONFIG,
                Bundle().apply { putInt(TargetConfigProtocol.SUB_ID, target) }, "$id-identity-$target", epoch)
            requireClean(result)
            val snapshot = TargetConfigProtocol.snapshot(target, result)
            if (snapshot.error != null || snapshot.identity.isBlank()) {
                throw OperationNotStartedException("Cannot verify active SIM identity")
            }
            snapshot.identity
        }
    }

    private fun awaitLate(task: Deferred<Outcome>, record: OperationRecord, args: Bundle) {
        scope.launch {
            val outcome = task.await()
            OperationCoordinator.serialized {
                if (machine.activeOperationId == record.operationId && machine.status.epoch == record.epoch) {
                    finishLocked(record, outcome, args)
                }
            }
        }
    }

    private fun finishLocked(record: OperationRecord, outcome: Outcome, args: Bundle): Bundle? {
        val failure = outcome.failure
        if (failure != null && failure !is OperationNotStartedException) {
            Log.e(TAG, "Operation outcome is unconfirmed", failure)
            markUnknown(record)
            return errorResult(args, "操作或清理结果尚未确认，请先恢复核对", "OPERATION_INDETERMINATE")
        }
        if (outcome.result?.getString(BridgeProtocol.ERROR_CODE) == "OPERATION_INDETERMINATE") {
            machine.uncertain(record.operationId, record.epoch)
            preferences().edit().putBoolean("restore_paused", true).commit()
        }
        val wasUnknown = machine.status.connection == ConnectionState.RECOVERY_REQUIRED
        try {
            updatePending { it.copy(phase = OperationPhase.TERMINAL, cleanupConfirmed = true) }
            if (!wasUnknown) { journal.clear(); pending = null }
        } catch (journalFailure: Exception) {
            Log.e(TAG, "Operation journal completion failed", journalFailure)
            markUnknown(record)
            return errorResult(args, "操作记录尚未完成，请刷新核对", "JOURNAL_FAILED")
        }
        machine.finish(record.operationId, record.epoch, true)
        publish()
        return if (failure != null) errorResult(args, failure.message ?: "操作未启动", "NOT_STARTED") else outcome.result
    }

    private fun markUnknown(record: OperationRecord) {
        machine.uncertain(record.operationId, record.epoch)
        preferences().edit().putBoolean("restore_paused", true).commit()
        try { updatePending { it.copy(phase = OperationPhase.UNKNOWN, cleanupConfirmed = false) } }
        catch (failure: Exception) { Log.e(TAG, "Could not update unknown operation journal", failure) }
        publish()
    }

    private suspend fun connectLocked() {
        if (machine.status.mode == BackendMode.UNSET) { publish(); return }
        val connection = backend(machine.status.mode).connect(context, machine.status.epoch)
        machine.updateConnection(if (connection.isReady && preferences().getBoolean("restore_paused", false))
            connection.copy(message = "自动恢复因上次未知结果暂停；核对配置后，请将开关关闭再开启") else connection)
        publish()
    }

    private suspend fun reconcileLocked() {
        val record = pending ?: return
        if (corruptJournal || recoveryInFlight || !oldOperationEnded(record)) return
        val selected = backend(machine.status.mode)
        val connection = selected.connect(context, machine.status.epoch)
        if (!connection.isReady || record.mode != machine.status.mode) { publish(); return }
        // 核对也会建立权限委托；先持久化其未知阶段，超时后不能启动第二次核对。
        updatePending { it.copy(phase = OperationPhase.UNKNOWN, cleanupConfirmed = false, bootCount = bootCount()) }
        recoveryInFlight = true
        recoveryBackupPresent = false
        publish()
        val task = scope.async {
            try { readBack(selected, record) }
            catch (failure: OperationNotStartedException) { false }
            catch (failure: Throwable) {
                Log.w(TAG, "Recovery readback outcome is unknown", failure)
                null
            }
        }
        val observed = withTimeoutOrNull(TIMEOUT_MS) { RecoveryObservation(task.await()) }
        if (observed != null) finishReadback(observed.verified, connection)
        else scope.launch {
            val result = task.await()
            OperationCoordinator.serialized { finishReadback(result, connection) }
        }
    }

    private fun finishReadback(verified: Boolean?, connection: BackendStatus) {
        recoveryInFlight = false
        if (verified != null) updatePending { it.copy(phase = OperationPhase.TERMINAL, cleanupConfirmed = true) }
        if (verified == true) {
            check(preferences().edit().putBoolean("restore_paused", true).commit()) { "Cannot persist automatic restore pause" }
            journal.clear()
            pending = null
            if (machine.activeOperationId != null) machine.finish(machine.activeOperationId!!, machine.status.epoch, true)
            machine.reconcile(true, true, true)
            machine.updateConnection(connection.copy(message = machine.status.message))
        }
        publish()
    }

    private suspend fun readBack(selected: PrivilegeBackend, record: OperationRecord, permitBackup: Boolean = false): Boolean {
        val operation = OperationType.valueOf(record.operation)
        val epoch = machine.status.epoch
        val id = UUID.randomUUID().toString()
        if (!record.dispatched || operation == OperationType.READ_SIMS) {
            val result = selected.execute(context, OperationType.READ_SIMS, Bundle(), id, epoch)
            requireClean(result)
            return result?.containsKey(SimReader.BUNDLE_RESULT) == true
        }
        val targets = if (operation in captiveWrites || operation == OperationType.READ_CAPTIVE_PORTAL) emptyMap()
            else captureIdentities(selected, record.subId, id, epoch)
        if (record.targetIdentities.isNotEmpty() && record.targetIdentities != targets) return false
        if (record.identityDigest != null && targets[record.subId] != record.identityDigest) return false
        if (operation in persistentWrites || operation == OperationType.READ_PERSISTENT_VOLTE) {
            var hasBackup = false
            for (subId in targets.keys) {
                val result = selected.execute(context, OperationType.READ_PERSISTENT_VOLTE, Bundle().apply {
                    putInt(PersistentVolteModifier.SUB_ID, subId)
                    putString(PersistentVolteModifier.ACTION, PersistentVolteModifier.QUERY)
                }, "$id-volte-$subId", epoch)
                requireClean(result)
                if (result == null || !result.getBoolean(PersistentVolteModifier.COMPLETED) ||
                    result.getString(PersistentVolteModifier.ERROR) != null ||
                    !result.containsKey(PersistentVolteModifier.OPT_IN) || !result.containsKey(PersistentVolteModifier.USER_ENABLED)) return false
                hasBackup = hasBackup || result.getBoolean(PersistentVolteModifier.CAN_RESTORE)
            }
            recoveryBackupPresent = hasBackup && operation in persistentWrites
            return permitBackup || !hasBackup
        }
        if (operation in captiveWrites || operation == OperationType.READ_CAPTIVE_PORTAL) {
            val result = selected.execute(context, OperationType.READ_CAPTIVE_PORTAL, Bundle().apply {
                putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_READ)
            }, id, epoch)
            requireClean(result)
            return result?.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS) == true &&
                result.containsKey(CaptivePortalSettingsModifier.RESULT_HTTP_URL) &&
                result.containsKey(CaptivePortalSettingsModifier.RESULT_HTTPS_URL)
        }
        if (operation == OperationType.RESET_IMS || operation == OperationType.READ_CAPABILITIES) {
            return targets.keys.all { subId ->
                val result = selected.execute(context, OperationType.READ_CAPABILITIES, Bundle().apply {
                    putInt(ImsCapabilityReader.BUNDLE_SELECT_SIM_ID, subId)
                }, "$id-ims-$subId", epoch)
                requireClean(result)
                result != null && result.containsKey(ImsCapabilityReader.BUNDLE_IMS_REGISTERED) &&
                    result.getString(ImsCapabilityReader.BUNDLE_RESULT_MSG) == null
            }
        }
        // captureIdentities 已对每张原目标卡读取完整 CarrierConfig，不保存成功历史。
        return targets.isNotEmpty()
    }

    suspend fun recoverPersistentVolte(): String? = try {
        recoverPersistentVolteLocked()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.e(TAG, "Explicit recovery remains blocked", failure)
        publish()
        "恢复无法确认，原值备份与操作记录已保留，请稍后核对"
    }

    private suspend fun recoverPersistentVolteLocked(): String? {
        if (!initialized) return "后端尚未初始化"
        var entered = false
        val result = OperationCoordinator.tryExclusive {
            entered = true
            val record = pending ?: return@tryExclusive "没有需要恢复的持久化 VoLTE 操作"
            if (recoveryInFlight || !record.dispatched || record.targetIdentities.isEmpty() ||
                record.operation !in persistentWrites.map { it.name } || !oldOperationEnded(record)) {
                return@tryExclusive "旧操作尚未确认结束，请等待或重启设备后核对"
            }
            val selected = backend(machine.status.mode)
            val connection = selected.connect(context, machine.status.epoch)
            if (record.mode != machine.status.mode || !connection.isReady) {
                return@tryExclusive "请先恢复当前所选后端连接"
            }
            // 必须在启动恢复任务之前落盘，避免恢复本身在进程重建后被再次执行。
            updatePending { it.copy(phase = OperationPhase.UNKNOWN, cleanupConfirmed = false, bootCount = bootCount(), dispatched = true) }
            recoveryInFlight = true
            _canRecoverPersistentVolte.value = false
            publish()
            val task = scope.async {
                try {
                    if (!readBack(selected, record, permitBackup = true)) throw OperationNotStartedException("SIM identity or current state could not be verified")
                    val epoch = machine.status.epoch
                    for ((subId, identity) in record.targetIdentities.ifEmpty {
                        captureIdentities(selected, record.subId, UUID.randomUUID().toString(), epoch)
                    }) {
                        val restored = selected.execute(context, OperationType.RESTORE_PERSISTENT_VOLTE, Bundle().apply {
                            putInt(PersistentVolteModifier.SUB_ID, subId)
                            putString(PersistentVolteModifier.ACTION, PersistentVolteModifier.RESTORE)
                            putString(BridgeProtocol.TARGET_IDENTITY, identity)
                        }, UUID.randomUUID().toString(), epoch)
                        requireClean(restored)
                        if (restored?.getBoolean(PersistentVolteModifier.COMPLETED) != true) throw OperationUncertainException("Original VoLTE settings could not be restored")
                    }
                    if (!readBack(selected, record)) throw OperationUncertainException("Restored VoLTE readback is unconfirmed")
                    Outcome()
                } catch (failure: Throwable) { Outcome(failure = failure) }
            }
            val outcome = withTimeoutOrNull(TIMEOUT_MS) { task.await() }
            if (outcome?.failure == null && outcome != null) {
                recoveryInFlight = false
                check(preferences().edit().putBoolean("restore_paused", true).commit()) { "Cannot persist automatic restore pause" }
                journal.clear(); pending = null
                if (machine.activeOperationId != null) machine.finish(machine.activeOperationId!!, machine.status.epoch, true)
                machine.reconcile(true, true, true)
                machine.updateConnection(connection.copy(message = machine.status.message))
                publish()
                null
            } else {
                scope.launch {
                    val late = task.await()
                    OperationCoordinator.serialized {
                        recoveryInFlight = false
                        if (late.failure == null || late.failure is OperationNotStartedException) {
                            updatePending { it.copy(phase = OperationPhase.TERMINAL, cleanupConfirmed = true) }
                        }
                        publish()
                    }
                }
                "恢复结果尚未确认；原值备份和操作记录已保留，请刷新核对"
            }
        }
        return if (entered) result else "当前正在核对，请稍后再恢复"
    }

    private fun oldOperationEnded(record: OperationRecord): Boolean =
        RecoveryPolicy.oldOperationEnded(record, bootCount())

    private fun publish() {
        _status.value = machine.status
        val recoveryConnectionSafe = !corruptJournal && machine.status.connection == ConnectionState.RECOVERY_REQUIRED &&
            RecoveryPolicy.mayReconnect(pending, machine.status.mode, bootCount(), recoveryInFlight || machine.activeOperationId != null)
        _canStartEmbeddedForRecovery.value = recoveryConnectionSafe && machine.status.mode == BackendMode.EMBEDDED
        _canRequestOfficialPermissionForRecovery.value = recoveryConnectionSafe && machine.status.mode == BackendMode.OFFICIAL
        _canRecoverPersistentVolte.value = recoveryConnectionSafe && recoveryBackupPresent
    }

    private fun backend(mode: BackendMode): PrivilegeBackend = when (mode) {
        BackendMode.OFFICIAL -> official
        BackendMode.EMBEDDED -> embedded
        BackendMode.UNSET -> error("No backend selected")
    }
    private fun preferences() = context.getSharedPreferences("privilege_backend", Context.MODE_PRIVATE)
    private fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun savePending(record: OperationRecord) = synchronized(journalGuard) { journal.write(record); pending = record }
    private fun updatePending(change: (OperationRecord) -> OperationRecord) = synchronized(journalGuard) {
        pending?.let { val updated = change(it); journal.write(updated); pending = updated }
    }
    private fun subId(args: Bundle) = when {
        args.containsKey(TargetConfigProtocol.SUB_ID) -> args.getInt(TargetConfigProtocol.SUB_ID, -1)
        args.containsKey(PersistentVolteModifier.SUB_ID) -> args.getInt(PersistentVolteModifier.SUB_ID, -1)
        else -> args.getInt(ImsModifier.BUNDLE_SELECT_SIM_ID, -1)
    }
    private fun requireClean(result: Bundle?) {
        if (result != null && !result.getBoolean(BridgeProtocol.CLEANUP_CONFIRMED)) {
            throw OperationUncertainException("Backend did not confirm permission cleanup")
        }
    }
    private fun shouldFallback(result: Bundle?): Boolean = BrokerRetryPolicy.allowed(
        hasResult = result != null,
        explicitlyAllowed = result?.getBoolean(BridgeProtocol.BROKER_RETRY_ALLOWED) == true,
        errorCode = result?.getString(BridgeProtocol.ERROR_CODE),
    )
    private fun errorResult(args: Bundle?, message: String, code: String) = Bundle().apply {
        putString(BridgeProtocol.ERROR_CODE, code)
        putString(BridgeProtocol.MESSAGE, message)
        putBoolean(ImsModifier.BUNDLE_RESULT, false); putString(ImsModifier.BUNDLE_RESULT_MSG, message)
        putBoolean(PersistentVolteModifier.COMPLETED, false); putString(PersistentVolteModifier.ERROR, message)
        putBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS, false); putString(CaptivePortalSettingsModifier.RESULT_MESSAGE, message)
        putBoolean(TargetConfigProtocol.COMPLETE, true); putString(TargetConfigProtocol.ERROR, message)
        putInt(TargetConfigProtocol.SUB_ID, args?.let(::subId) ?: -1)
    }
    private data class RecoveryObservation(val verified: Boolean?)
    private data class Outcome(val result: Bundle? = null, val failure: Throwable? = null)
    private val persistentWrites = setOf(OperationType.SET_PERSISTENT_VOLTE, OperationType.RESTORE_PERSISTENT_VOLTE)
    private val captiveWrites = setOf(OperationType.WRITE_CAPTIVE_PORTAL, OperationType.RESET_CAPTIVE_PORTAL)
}
