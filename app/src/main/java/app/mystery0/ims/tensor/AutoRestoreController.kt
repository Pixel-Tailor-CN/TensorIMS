package app.mystery0.ims.tensor

import android.content.Context
import android.util.Log
import app.mystery0.ims.tensor.privileged.ImsModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime

/** 只在已选后端就绪时恢复配置；不启动服务、不配对，也不恢复持久化 VoLTE。 */
class AutoRestoreController(private val context: Context) {
    val repository = ConfigurationRepository(context)
    private val notifier = AutoRestoreNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _enabled = MutableStateFlow(repository.enabled)
    val enabled = _enabled.asStateFlow()
    private var restoreJob: Job? = null
    private var started = false
    private var permissionPending = false

    fun start() {
        if (started) return
        started = true
        PrivilegeRuntime.initialize(context)
        scope.launch {
            PrivilegeRuntime.status.collect { status ->
                if (status.connection != ConnectionState.BUSY) permissionPending = false
                if (status.isReady) schedule()
                else if (status.mode == BackendMode.OFFICIAL && status.errorCode == "PERMISSION_REQUIRED") {
                    maybeRequestPermission()
                }
                // 正在执行的写入由 Runtime 保持，不能通过取消监听任务提前释放安全门。
            }
        }
    }

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        repository.enabled = value
        _enabled.value = value
        if (value) {
            repository.beginOptIn()
            PrivilegeRuntime.acknowledgeAutomaticRestore()
            schedule()
            maybeRequestPermission()
        }
        // 不取消正在执行的 Instrumentation：等待其返回并记录结果，下一次写入前再检查开关。
    }

    fun requestPermission(@Suppress("UNUSED_PARAMETER") requestCode: Int) {
        if (permissionPending) return
        permissionPending = true
        PrivilegeRuntime.requestOfficialPermission()
    }

    private fun maybeRequestPermission() {
        if (!_enabled.value || permissionPending || !repository.hasRestorableHistory()) return
        val boot = repository.bootCount()
        if (boot >= 0 && PrivilegeRuntime.canRequestOfficialPermissionAutomatically() && repository.claimPermissionRequest(boot)) {
            Log.i(TAG, "Requesting selected official backend permission for automatic restore")
            requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    fun schedule() {
        val epoch = PrivilegeRuntime.status.value.epoch
        if (!_enabled.value || restoreJob?.isActive == true || !PrivilegeRuntime.canAutoRestore(epoch)) return
        restoreJob = scope.launch {
            val results = mutableMapOf<Int, Boolean>()
            try {
                if (!repository.hasRestorableHistory()) return@launch
                val boot = repository.bootCount()
                if (boot < 0) {
                    Log.w(TAG, "Boot count unavailable; skipping automatic restore")
                    return@launch
                }
                repeat(MAX_ATTEMPTS) { attempt ->
                    if (!_enabled.value || !PrivilegeRuntime.canAutoRestore(epoch)) return@launch
                    val complete = ConfigurationOperations.run {
                        if (!_enabled.value || !PrivilegeRuntime.canAutoRestore(epoch)) return@run true
                        val sims = ShizukuProvider.readSimInfoList(context)
                        if (sims.isEmpty()) return@run false
                        var success = true
                        for (sim in sims) {
                            if (!_enabled.value || !PrivilegeRuntime.canAutoRestore(epoch)) return@run true
                            val target = repository.restoreTarget(sim.subId) ?: continue
                            if (repository.wasApplied(sim.subId, boot, target)) continue
                            // 全卡历史中的名称字段仍遵循手动全卡应用规则，不得转为单卡名称覆盖。
                            val error = if (target.version >= 2) {
                                ShizukuProvider.applyTargetConfig(context, sim.subId, target.identity, target.config) {
                                    _enabled.value && PrivilegeRuntime.status.value.epoch == epoch
                                }.error
                            } else {
                                // 旧历史保留“false 不下发”的构造方式，升级不能改变原始意图。
                                val bundle = ConfigurationRepository.buildBundle(target.sourceSubId, target.config)
                                    .apply { putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, sim.subId) }
                                ShizukuProvider.overrideImsConfig(context, bundle) {
                                    _enabled.value && PrivilegeRuntime.status.value.epoch == epoch
                                }
                            }
                            if (error == null) {
                                results[sim.subId] = true
                                repository.markApplied(sim.subId, boot, target)
                                Log.i(TAG, "Automatically restored config for subId=${sim.subId}")
                            } else {
                                // 用户关闭后未启动的调用不算应用失败，也不触发结果提示。
                                if (_enabled.value) results[sim.subId] = false
                                success = false
                                Log.w(TAG, "Automatic restore failed for subId=${sim.subId}: $error")
                            }
                        }
                        success
                    }
                    if (complete) return@launch
                    if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
                }
                Log.w(TAG, "Automatic restore retry limit reached")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Automatic restore failed", e)
            } finally {
                // 一轮有限重试只汇总一次，失败后重试成功的 SIM 不再计入失败。
                if (results.isNotEmpty()) {
                    notifier.showResult(results.count { it.value }, results.count { !it.value })
                }
            }
        }
    }

    fun close() {
        scope.cancel()
        notifier.close()
    }

    companion object {
        private const val TAG = "AutoRestore"
        private const val PERMISSION_REQUEST_CODE = 0x4152
        private const val MAX_ATTEMPTS = 6
        private const val RETRY_DELAY_MS = 10_000L
    }
}
