package app.mystery0.ims.tensor.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.ConfigurationOperations
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.ShizukuProvider
import app.mystery0.ims.tensor.model.ImsCapabilityStatus
import app.mystery0.ims.tensor.model.PersistentVolteState
import app.mystery0.ims.tensor.embedded.EmbeddedLauncher
import app.mystery0.ims.tensor.embedded.WirelessAdbService
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import app.mystery0.ims.tensor.ui.SimListUiState
import app.mystery0.ims.tensor.ui.completeSimListRead
import app.mystery0.ims.tensor.privilege.SimReadResult
import app.mystery0.ims.tensor.ui.BackendAction
import app.mystery0.ims.tensor.ui.BackendActionState
import app.mystery0.ims.tensor.ui.backendUiActions
import app.mystery0.ims.tensor.ui.canConfirmPersistentRecovery
import app.mystery0.ims.tensor.ui.canConfirmModeChoice
import app.mystery0.ims.tensor.model.asLegacyUiStatus
import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.model.SystemInfo
import app.mystery0.ims.tensor.privileged.ImsModifier
import app.mystery0.ims.tensor.privileged.PersistentVolteModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面的 ViewModel，负责管理 UI 状态和业务逻辑。
 * 包括所选后端状态监听、手动连接、系统信息加载及 IMS 配置的读写。
 */
class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private var toast: Toast? = null
    private val operationGate = OperationGate()

    private val autoRestore = (application as app.mystery0.ims.tensor.Application).autoRestore
    private val configurations = autoRestore.repository
    val autoRestoreEnabled = autoRestore.enabled
    val backendStatus = PrivilegeRuntime.status
    val wirelessAdbState = WirelessAdbService.state
    val canRecoverPersistentVolte = PrivilegeRuntime.canRecoverPersistentVolte
    val canStartEmbeddedForRecovery = PrivilegeRuntime.canStartEmbeddedForRecovery
    val canRequestOfficialPermissionForRecovery = PrivilegeRuntime.canRequestOfficialPermissionForRecovery
    private val _backendAction = MutableStateFlow(BackendActionState())
    val backendAction = _backendAction.asStateFlow()
    private val launcher = EmbeddedLauncher(application)
    private val uiPreferences = application.getSharedPreferences("backend_ui", Application.MODE_PRIVATE)
    private val _migrationNotice = MutableStateFlow(!uiPreferences.getBoolean("migration_notice_seen", false))
    val migrationNotice = _migrationNotice.asStateFlow()
    val isOperationInProgress = combine(ConfigurationOperations.busy, backendStatus, backendAction, wirelessAdbState) { busy, status, action, wireless ->
        busy || status.connection in setOf(ConnectionState.BUSY, ConnectionState.SWITCHING, ConnectionState.CONNECTING) || action.inProgress || wireless.active
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun acknowledgeMigrationNotice() {
        uiPreferences.edit().putBoolean("migration_notice_seen", true).apply()
        _migrationNotice.value = false
    }

    fun chooseBackend(mode: BackendMode) {
        if (!canConfirmModeChoice(backendStatus.value, mode, _backendAction.value.inProgress, backendActionBusy())) {
            toast(application.getString(R.string.backend_switch_busy), false)
            return
        }
        runBackendAction(BackendAction.CHOOSE_MODE) { PrivilegeRuntime.chooseMode(mode) }
    }

    fun recoverPersistentVolte() {
        if (!canConfirmPersistentRecovery(backendStatus.value, canRecoverPersistentVolte.value,
                _backendAction.value.inProgress, backendActionBusy())) return
        runBackendAction(BackendAction.RECOVER) { PrivilegeRuntime.recoverPersistentVolte() }
    }

    fun startEmbeddedRoot() = startEmbeddedAction(BackendAction.ROOT) { launcher.startRoot() }

    fun startEmbeddedPairing(): Boolean = startWirelessAction(pairing = true)

    fun startEmbeddedWireless(): Boolean = startWirelessAction(pairing = false)

    fun cancelEmbeddedWireless() = WirelessAdbService.cancel(application)

    private fun startWirelessAction(pairing: Boolean): Boolean {
        if (!backendUiActions(backendStatus.value, _backendAction.value.inProgress, backendActionBusy(),
                recoveryStartAllowed = canStartEmbeddedForRecovery.value).canStartEmbedded) return false
        _backendAction.value = BackendActionState()
        // 服务同步占用无线动作状态，再由前台服务持有配对流程；旋转或离开页面不会重复启动。
        if (pairing) WirelessAdbService.startPairing(application) else WirelessAdbService.startConnect(application)
        return wirelessAdbState.value.active
    }

    private fun startEmbeddedAction(action: BackendAction, block: suspend () -> String?) {
        if (!backendUiActions(backendStatus.value, _backendAction.value.inProgress, backendActionBusy(),
                recoveryStartAllowed = canStartEmbeddedForRecovery.value).canStartEmbedded) return
        // 配对与两种启动都由运行时的同一门禁保护，避免与切换或恢复发生竞态。
        runBackendAction(action) { PrivilegeRuntime.startEmbedded(block) }
    }

    private fun runBackendAction(action: BackendAction, block: suspend () -> String?) {
        if (_backendAction.value.inProgress || wirelessAdbState.value.active) return
        _backendAction.value = BackendActionState(action = action)
        viewModelScope.launch {
            try {
                val error = block()
                _backendAction.value = BackendActionState(
                    notice = if (error == null) when (action) {
                        BackendAction.RECOVER -> R.string.backend_recovery_success
                        BackendAction.PAIR -> null
                        BackendAction.ROOT, BackendAction.WIRELESS -> R.string.backend_start_success
                        BackendAction.CHOOSE_MODE -> null
                    } else null,
                    error = error,
                )
            } catch (cancelled: CancellationException) {
                _backendAction.value = BackendActionState()
                throw cancelled
            } catch (e: Exception) {
                // 不记录启动参数；尤其不能记录无线配对码或密钥。
                Log.w("MainViewModel", "Backend action failed")
                _backendAction.value = BackendActionState(error = e.javaClass.simpleName)
            } finally {
                drainPendingPersistentRefresh()
            }
        }
    }

    fun setAutoRestoreEnabled(enabled: Boolean) = autoRestore.setEnabled(enabled)

    private val _persistentVolteState = MutableStateFlow<PersistentVolteState?>(null)
    val persistentVolteState = _persistentVolteState.asStateFlow()
    private var persistentVolteSubId: Int? = null
    private var pendingPersistentRefresh = false

    fun selectPersistentVolteSim(subId: Int?) {
        persistentVolteSubId = subId?.takeIf { it >= 0 }
        _persistentVolteState.value = persistentVolteSubId?.let { PersistentVolteState(it) }
        refreshPersistentVolte()
    }

    fun refreshPersistentVolte() {
        val subId = persistentVolteSubId ?: return
        if (_shizukuStatus.value != ShizukuStatus.READY) return
        if (backendOperationBusy()) {
            pendingPersistentRefresh = true
            return
        }
        launchExclusiveOperation {
            publishPersistentVolte(ShizukuProvider.persistentVolte(application, subId, PersistentVolteModifier.QUERY))
        }
    }

    fun onPersistentVolteChange(subId: Int, restore: Boolean) {
        if (subId < 0 || subId != persistentVolteSubId || _shizukuStatus.value != ShizukuStatus.READY) return
        launchExclusiveOperation {
            val result = ShizukuProvider.persistentVolte(
                application, subId,
                if (restore) PersistentVolteModifier.RESTORE else PersistentVolteModifier.ENABLE,
            )
            publishPersistentVolte(result)
            if (result.error == null) {
                toast(application.getString(if (restore) R.string.persistent_volte_restored else R.string.persistent_volte_applied))
            } else {
                toast(application.getString(R.string.config_failed, result.error), false)
            }
        }
    }

    private fun publishPersistentVolte(state: PersistentVolteState) {
        // 异步结果只更新对应的当前 SIM；后端断开后不再展示旧快照。
        if (state.subId == persistentVolteSubId && _shizukuStatus.value == ShizukuStatus.READY) {
            _persistentVolteState.value = state
        }
    }

    // 系统信息状态流
    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()

    // 旧业务页面使用兼容门禁；首页和设置直接显示真实 BackendStatus。
    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.CHECKING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    // 所有可用 SIM 卡列表流
    private val _allSimList = MutableStateFlow<List<SimSelection>>(emptyList())
    val allSimList: StateFlow<List<SimSelection>> = _allSimList.asStateFlow()
    private val _simReadError = MutableStateFlow<String?>(null)
    val simReadError = _simReadError.asStateFlow()

    private var loadedEpoch: Long? = null
    private var readingSims = false

    init {
        PrivilegeRuntime.initialize(application)
        viewModelScope.launch {
            ConfigurationOperations.busy.collect { busy ->
                if (!busy) drainPendingPersistentRefresh()
            }
        }
        viewModelScope.launch {
            wirelessAdbState.collect { wireless ->
                if (!wireless.active) drainPendingPersistentRefresh()
            }
        }
        viewModelScope.launch {
            backendStatus.collect { status ->
                // BUSY 仍保留已认证的页面快照，避免每次读取都丢失草稿；动作另受忙碌门禁保护。
                _shizukuStatus.value = status.asLegacyUiStatus()
                if (status.isReady && loadedEpoch != status.epoch) {
                    loadedEpoch = status.epoch
                    loadSimList()
                }
                if (status.isReady) drainPendingPersistentRefresh()
            }
        }
        autoRestore.schedule()
        loadSystemInfo()
    }

    /** 刷新只检查当前所选后端，不请求授权或启动服务。 */
    fun refreshBackendStatus() {
        if (wirelessAdbState.value.active) return
        PrivilegeRuntime.refresh()
        loadSimList()
    }

    fun requestOfficialPermission() {
        if (backendUiActions(backendStatus.value, _backendAction.value.inProgress, backendActionBusy(),
                recoveryPermissionAllowed = canRequestOfficialPermissionForRecovery.value).canRequestOfficialPermission) PrivilegeRuntime.requestOfficialPermission()
    }

    /**
     * 通过所选后端读取设备上的 SIM 卡信息。
     * 并在列表头部添加“所有 SIM 卡”选项。
     */
    fun loadSimList() {
        val status = backendStatus.value
        if (!status.isReady || readingSims) return
        readingSims = true
        viewModelScope.launch {
            try {
                val result = ShizukuProvider.readSimInfoResult(application)
                publishSimRead(result, status.epoch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                publishSimRead(SimReadResult(emptyList(), application.getString(R.string.backend_sim_read_error)), status.epoch)
            } finally {
                readingSims = false
                if (backendStatus.value.epoch != status.epoch && backendStatus.value.isReady) loadSimList()
            }
        }
    }

    private fun publishSimRead(result: SimReadResult<SimSelection>, requestedEpoch: Long) {
        val current = SimListUiState(_allSimList.value, _simReadError.value)
        val all = SimSelection(-1, "", "", -1, application.getString(R.string.all_sim))
        val next = completeSimListRead(current, result, requestedEpoch, backendStatus.value.epoch, all)
        // 失败保留最后成功列表，只有成功的空结果才清理失效选择。
        _allSimList.value = next.items
        _simReadError.value = next.error
    }

    /**
     * 加载当前应用和系统的基本信息。
     */
    private fun loadSystemInfo() {
        viewModelScope.launch {
            _systemInfo.value = SystemInfo(
                appVersionName = BuildConfig.VERSION_NAME,
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                systemVersion = Build.DISPLAY,
                securityPatchVersion = Build.VERSION.SECURITY_PATCH,
            )
        }
    }

    /**
     * 通过所选后端读取系统当前实时 IMS 能力状态。
     */
    suspend fun loadRealSystemConfig(subId: Int): ImsCapabilityStatus? {
        return ShizukuProvider.readImsCapabilities(application, subId)
    }

    /**
     * 重置选中 SIM 卡的配置到运营商默认状态。
     */
    fun onResetConfiguration(selectedSim: SimSelection) {
        launchExclusiveOperation {
            val restored = ShizukuProvider.persistentVolte(
                application, selectedSim.subId, PersistentVolteModifier.RESTORE_FOR_RESET,
            )
            pendingPersistentRefresh = true
            if (restored.error != null) {
                toast(application.getString(R.string.config_failed, restored.error), false)
                return@launchExclusiveOperation
            }
            val bundle = ImsModifier.buildResetBundle()
            bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, selectedSim.subId)
            val resultMsg = ShizukuProvider.overrideImsConfig(application, bundle)
            if (resultMsg == null) {
                configurations.recordReset(selectedSim.subId)
                toast(application.getString(R.string.config_success_reset_message))
            } else {
                toast(application.getString(R.string.config_failed, resultMsg), false)
            }
        }
    }

    fun onResetIms(simSelection: SimSelection) {
        launchExclusiveOperation {
            try {
                val error = ShizukuProvider.resetIms(application, simSelection.subId)
                if (error == null) {
                    toast(application.getString(R.string.restart_ims_success))
                } else {
                    toast(application.getString(R.string.restart_ims_failed, error), false)
                }
            } catch (e: Exception) {
                toast(application.getString(R.string.restart_ims_failed, e.localizedMessage), false)
            }
        }
    }

    private fun launchExclusiveOperation(block: suspend () -> Unit) {
        if (backendOperationBusy() || !operationGate.tryEnter()) return
        viewModelScope.launch {
            try {
                ConfigurationOperations.run { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("MainViewModel", "Configuration operation failed", e)
                toast(application.getString(R.string.config_failed, e.localizedMessage), false)
            } finally {
                operationGate.leave()
                drainPendingPersistentRefresh()
            }
        }
    }

    private fun backendActionBusy(): Boolean = ConfigurationOperations.busy.value || wirelessAdbState.value.active

    private fun backendOperationBusy(): Boolean = backendActionBusy() ||
        !backendStatus.value.isReady || _backendAction.value.inProgress

    private fun drainPendingPersistentRefresh() {
        // 自动恢复释放业务锁也要刷新；手动操作尚未离开本地门闩时留给 finally 处理。
        if (!pendingPersistentRefresh || backendOperationBusy() || operationGate.isOccupied) return
        pendingPersistentRefresh = false
        refreshPersistentVolte()
    }

    private fun toast(msg: String, short: Boolean = true) {
        toast?.cancel()
        toast =
            Toast.makeText(application, msg, if (short) Toast.LENGTH_SHORT else Toast.LENGTH_LONG)
        toast?.show()
    }
}
