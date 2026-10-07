package app.mystery0.ims.tensor.ui

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mystery0.ims.tensor.ui.screens.AppSettingsScreen
import app.mystery0.ims.tensor.viewmodel.AppSettingsViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.ui.components.BackendChoiceDialog
import app.mystery0.ims.tensor.ui.screens.BackendSettingsScreen
import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.ui.navigation.TensorImsRoutes
import app.mystery0.ims.tensor.ui.navigation.activityLikeEnterTransition
import app.mystery0.ims.tensor.ui.navigation.activityLikeExitTransition
import app.mystery0.ims.tensor.ui.navigation.activityLikePopEnterTransition
import app.mystery0.ims.tensor.ui.navigation.activityLikePopExitTransition
import app.mystery0.ims.tensor.ui.screens.AdvancedToolsScreen
import app.mystery0.ims.tensor.ui.screens.CaptivePortalScreen
import app.mystery0.ims.tensor.ui.screens.HomeScreen
import app.mystery0.ims.tensor.ui.screens.ImsConfigScreen
import app.mystery0.ims.tensor.ui.screens.SystemNetworkScreen
import app.mystery0.ims.tensor.viewmodel.MainViewModel
import app.mystery0.ims.tensor.viewmodel.ImsConfigViewModel
import app.mystery0.ims.tensor.viewmodel.SystemNetworkViewModel

class MainActivity : BaseActivity() {
    private val appSettingsViewModel: AppSettingsViewModel by viewModels()
    private val viewModel: MainViewModel by viewModels()
    private val imsConfigViewModel: ImsConfigViewModel by viewModels()
    private val systemNetworkViewModel: SystemNetworkViewModel by viewModels()

    @Composable
    override fun content() {
        val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
        val canStartEmbeddedForRecovery by viewModel.canStartEmbeddedForRecovery.collectAsStateWithLifecycle()
        val canRequestOfficialPermissionForRecovery by viewModel.canRequestOfficialPermissionForRecovery.collectAsStateWithLifecycle()
        val canRecoverPersistentVolte by viewModel.canRecoverPersistentVolte.collectAsStateWithLifecycle()
        val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
        val backendAction by viewModel.backendAction.collectAsStateWithLifecycle()
        val wirelessAdbState by viewModel.wirelessAdbState.collectAsStateWithLifecycle()
        val showMigrationNotice by viewModel.migrationNotice.collectAsStateWithLifecycle()
        var firstChoiceDismissed by rememberSaveable { mutableStateOf(false) }
        val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
        val simReadError by viewModel.simReadError.collectAsStateWithLifecycle()
        val allSimList by viewModel.allSimList.collectAsStateWithLifecycle()
        val isOperationInProgress by viewModel.isOperationInProgress.collectAsStateWithLifecycle()
        val persistentVolteState by viewModel.persistentVolteState.collectAsStateWithLifecycle()
        val autoRestoreEnabled by viewModel.autoRestoreEnabled.collectAsStateWithLifecycle()
        val systemNetworkState by systemNetworkViewModel.uiState.collectAsStateWithLifecycle()

        var selectedSubId by rememberSaveable { mutableStateOf<Int?>(null) }
        val selectedSim = allSimList.firstOrNull { it.subId == selectedSubId }

        LaunchedEffect(allSimList, selectedSubId) {
            if (allSimList.isEmpty()) {
                selectedSubId = null
            } else if (selectedSim == null) {
                selectedSubId = allSimList.firstOrNull { it.subId != -1 }?.subId
                    ?: allSimList.first().subId
            }
        }

        LaunchedEffect(selectedSim?.subId, shizukuStatus) {
            viewModel.selectPersistentVolteSim(
                selectedSim?.subId?.takeIf {
                    it >= 0 && shizukuStatus == ShizukuStatus.READY
                },
            )
        }

        val navController = rememberNavController()
        fun navigate(route: String) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        val onBack: () -> Unit = { navController.popBackStack() }

        NavHost(
            navController = navController,
            startDestination = TensorImsRoutes.HOME,
            enterTransition = { activityLikeEnterTransition() },
            exitTransition = { activityLikeExitTransition() },
            popEnterTransition = { activityLikePopEnterTransition() },
            popExitTransition = { activityLikePopExitTransition() },
            predictivePopEnterTransition = { activityLikePopEnterTransition() },
            predictivePopExitTransition = { activityLikePopExitTransition() },
        ) {
            composable(TensorImsRoutes.HOME) {
                HomeScreen(
                    systemInfo = systemInfo,
                    backendStatus = backendStatus,
                    busy = isOperationInProgress,
                    showMigrationNotice = showMigrationNotice,
                    allSimList = allSimList,
                    simReadError = simReadError,
                    selectedSim = selectedSim,
                    autoRestoreEnabled = autoRestoreEnabled,
                    onSelectSim = { selectedSubId = it.subId },
                    onRefresh = viewModel::refreshBackendStatus,
                    onRequestPermission = viewModel::requestOfficialPermission,
                    onOpenBackendSettings = { navigate(TensorImsRoutes.BACKEND_SETTINGS) },
                    onAcknowledgeMigration = viewModel::acknowledgeMigrationNotice,
                    onOpenImsConfig = { navigate(TensorImsRoutes.IMS_CONFIG) },
                    onOpenSystemNetwork = { navigate(TensorImsRoutes.SYSTEM_NETWORK) },
                    onOpenAdvancedTools = { navigate(TensorImsRoutes.ADVANCED_TOOLS) },
                    onOpenAppSettings = { navigate(TensorImsRoutes.APP_SETTINGS) },
                    onOpenLogcat = {
                        startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                    },
                    onAutoRestoreEnabledChange = viewModel::setAutoRestoreEnabled,
                )
            }

            composable(TensorImsRoutes.APP_SETTINGS) {
                val languageTag by appSettingsViewModel.languageTag.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    appSettingsViewModel.refreshLanguage()
                    onPauseOrDispose { }
                }
                AppSettingsScreen(
                    languageTag = languageTag,
                    supportedLanguageTags = appSettingsViewModel.supportedLanguageTags,
                    onSelectLanguage = appSettingsViewModel::selectLanguage,
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.BACKEND_SETTINGS) {
                BackendSettingsScreen(
                    status = backendStatus,
                    action = backendAction,
                    wirelessState = wirelessAdbState,
                    busy = isOperationInProgress,
                    showMigrationNotice = showMigrationNotice,
                    canRecoverPersistentVolte = canRecoverPersistentVolte,
                    canStartEmbeddedForRecovery = canStartEmbeddedForRecovery,
                    canRequestOfficialPermissionForRecovery = canRequestOfficialPermissionForRecovery,
                    onRecoverPersistentVolte = viewModel::recoverPersistentVolte,
                    onChooseMode = viewModel::chooseBackend,
                    onRequestPermission = viewModel::requestOfficialPermission,
                    onRefresh = viewModel::refreshBackendStatus,
                    onPair = viewModel::startEmbeddedPairing,
                    onStartWireless = viewModel::startEmbeddedWireless,
                    onCancelWireless = viewModel::cancelEmbeddedWireless,
                    onStartRoot = viewModel::startEmbeddedRoot,
                    onAcknowledgeMigration = viewModel::acknowledgeMigrationNotice,
                    onOpenImsConfig = { navigate(TensorImsRoutes.IMS_CONFIG) },
                    onHome = { navController.popBackStack(TensorImsRoutes.HOME, false) },
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.IMS_CONFIG) {
                val editorState by imsConfigViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(selectedSim?.subId, shizukuStatus, allSimList.map { it.subId }) {
                    imsConfigViewModel.load(selectedSim?.subId, shizukuStatus == ShizukuStatus.READY)
                }
                ImsConfigScreen(
                    selectedSim = selectedSim,
                    shizukuStatus = shizukuStatus,
                    isOperationInProgress = isOperationInProgress,
                    state = editorState,
                    onEdit = imsConfigViewModel::edit,
                    onPreset = imsConfigViewModel::editAll,
                    onLoadHistory = imsConfigViewModel::loadHistory,
                    onRefresh = { imsConfigViewModel.load(selectedSim?.subId, shizukuStatus == ShizukuStatus.READY, discardEdits = true) },
                    onApply = imsConfigViewModel::apply,
                    onOpenReset = {
                        navController.popBackStack()
                        navigate(TensorImsRoutes.ADVANCED_TOOLS)
                    },
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.SYSTEM_NETWORK) {
                SystemNetworkScreen(
                    state = systemNetworkState,
                    onRefresh = systemNetworkViewModel::loadCaptivePortal,
                    onDiscardDraftAndReload = systemNetworkViewModel::discardDraftAndReload,
                    onOpenCaptivePortal = { navigate(TensorImsRoutes.CAPTIVE_PORTAL) },
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.CAPTIVE_PORTAL) {
                CaptivePortalScreen(
                    state = systemNetworkState,
                    shizukuStatus = shizukuStatus,
                    onRefresh = systemNetworkViewModel::loadCaptivePortal,
                    onDiscardDraftAndReload = systemNetworkViewModel::discardDraftAndReload,
                    onModeChange = systemNetworkViewModel::setMode,
                    onHttpUrlChange = systemNetworkViewModel::setHttpUrl,
                    onHttpsUrlChange = systemNetworkViewModel::setHttpsUrl,
                    onSave = systemNetworkViewModel::saveCaptivePortal,
                    onNoticeShown = systemNetworkViewModel::clearNotice,
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.ADVANCED_TOOLS) {
                AdvancedToolsScreen(
                    selectedSim = selectedSim,
                    shizukuStatus = shizukuStatus,
                    isOperationInProgress = isOperationInProgress,
                    persistentVolteState = persistentVolteState,
                    onLoadImsStatus = viewModel::loadRealSystemConfig,
                    onEnablePersistentVolte = {
                        viewModel.onPersistentVolteChange(it, restore = false)
                    },
                    onRestorePersistentVolte = {
                        viewModel.onPersistentVolteChange(it, restore = true)
                    },
                    onRefreshPersistentVolte = viewModel::refreshPersistentVolte,
                    onRestartIms = viewModel::onResetIms,
                    onResetConfiguration = viewModel::onResetConfiguration,
                    onBack = onBack,
                )
            }
        }
        if (backendStatus.mode == BackendMode.UNSET && !firstChoiceDismissed) {
            BackendChoiceDialog(
                currentMode = backendStatus.mode,
                enabled = backendUiActions(backendStatus, backendAction.inProgress, isOperationInProgress).canChooseMode,
                showMigration = showMigrationNotice,
                onChoose = { mode ->
                    firstChoiceDismissed = true
                    viewModel.chooseBackend(mode)
                    navigate(TensorImsRoutes.BACKEND_SETTINGS)
                },
                onDismiss = { firstChoiceDismissed = true },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBackendStatus()
        viewModel.refreshPersistentVolte()
    }
}
