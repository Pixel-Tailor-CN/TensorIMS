package io.github.vvb2060.ims.ui

import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.ui.navigation.TensorImsRoutes
import io.github.vvb2060.ims.ui.screens.AdvancedToolsScreen
import io.github.vvb2060.ims.ui.screens.CaptivePortalScreen
import io.github.vvb2060.ims.ui.screens.HomeScreen
import io.github.vvb2060.ims.ui.screens.ImsConfigScreen
import io.github.vvb2060.ims.ui.screens.SystemNetworkScreen
import io.github.vvb2060.ims.viewmodel.MainViewModel
import io.github.vvb2060.ims.viewmodel.SystemNetworkViewModel

class MainActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val systemNetworkViewModel: SystemNetworkViewModel by viewModels()

    @Composable
    override fun content() {
        val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
        val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
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
        ) {
            composable(TensorImsRoutes.HOME) {
                HomeScreen(
                    systemInfo = systemInfo,
                    shizukuStatus = shizukuStatus,
                    allSimList = allSimList,
                    selectedSim = selectedSim,
                    autoRestoreEnabled = autoRestoreEnabled,
                    onSelectSim = { selectedSubId = it.subId },
                    onRefresh = {
                        viewModel.updateShizukuStatus()
                        viewModel.loadSimList()
                    },
                    onRequestShizukuPermission = {
                        viewModel.requestShizukuPermission(0)
                    },
                    onOpenImsConfig = { navigate(TensorImsRoutes.IMS_CONFIG) },
                    onOpenSystemNetwork = { navigate(TensorImsRoutes.SYSTEM_NETWORK) },
                    onOpenAdvancedTools = { navigate(TensorImsRoutes.ADVANCED_TOOLS) },
                    onAutoRestoreEnabledChange = viewModel::setAutoRestoreEnabled,
                )
            }

            composable(TensorImsRoutes.IMS_CONFIG) {
                ImsConfigScreen(
                    selectedSim = selectedSim,
                    shizukuStatus = shizukuStatus,
                    isOperationInProgress = isOperationInProgress,
                    loadConfiguration = viewModel::loadConfiguration,
                    loadDefaults = viewModel::loadDefaultPreferences,
                    onApplyConfiguration = viewModel::onApplyConfiguration,
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.SYSTEM_NETWORK) {
                SystemNetworkScreen(
                    state = systemNetworkState,
                    onRefresh = systemNetworkViewModel::loadCaptivePortal,
                    onOpenCaptivePortal = { navigate(TensorImsRoutes.CAPTIVE_PORTAL) },
                    onBack = onBack,
                )
            }

            composable(TensorImsRoutes.CAPTIVE_PORTAL) {
                CaptivePortalScreen(
                    state = systemNetworkState,
                    shizukuStatus = shizukuStatus,
                    onRefresh = systemNetworkViewModel::loadCaptivePortal,
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
                    onOpenLogcat = {
                        startActivity(Intent(this@MainActivity, LogcatActivity::class.java))
                    },
                    onBack = onBack,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateShizukuStatus()
        viewModel.refreshPersistentVolte()
    }
}
