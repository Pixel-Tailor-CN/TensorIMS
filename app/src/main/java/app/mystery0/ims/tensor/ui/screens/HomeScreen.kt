package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import app.mystery0.ims.tensor.model.ImsCapabilityStatus
import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.model.asLegacyUiStatus
import app.mystery0.ims.tensor.ui.components.ImsStatusSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.BackendStatus
import androidx.compose.material.icons.rounded.Settings
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.model.SystemInfo
import app.mystery0.ims.tensor.ui.components.AutoRestoreCard
import app.mystery0.ims.tensor.ui.components.DeviceStatusCard
import app.mystery0.ims.tensor.ui.components.GroupedSettingsItem
import androidx.compose.foundation.layout.Arrangement

@Composable
fun HomeScreen(
    systemInfo: SystemInfo,
    backendStatus: BackendStatus,
    busy: Boolean,
    allSimList: List<SimSelection>,
    simReadError: String?,
    selectedSim: SimSelection?,
    autoRestoreEnabled: Boolean,
    onSelectSim: (SimSelection) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBackendSettings: () -> Unit,
    onOpenImsConfig: () -> Unit,
    onOpenSystemNetwork: () -> Unit,
    onOpenAdvancedTools: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAutoRestoreEnabledChange: (Boolean) -> Unit,
    onLoadImsStatus: suspend (Int) -> ImsCapabilityStatus?,
    onRestartIms: (SimSelection, (Boolean) -> Unit) -> Unit,
) {
    var showImsStatus by remember { mutableStateOf(false) }
    if (showImsStatus) {
        key(backendStatus.epoch, selectedSim?.subId) {
            ImsStatusSheet(
                sims = allSimList.filter { it.subId >= 0 },
                selectedSim = selectedSim,
                ready = backendStatus.asLegacyUiStatus() == ShizukuStatus.READY,
                busy = busy,
                onLoad = onLoadImsStatus,
                onRestart = onRestartIms,
                onOpenBackend = { showImsStatus = false; onOpenBackendSettings() },
                onDismiss = { showImsStatus = false },
            )
        }
    }
    Scaffold(
        // 顶部和横向避让系统栏/挖孔；底部允许正文绘制到透明导航栏后方。
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 标题和设置入口属于滚动内容，只在回到顶部时重新出现。
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets(0.dp),
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.app_settings))
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.for_pixel),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            DeviceStatusCard(
                systemInfo = systemInfo,
                backendStatus = backendStatus,
                busy = busy,
                onRefresh = onRefresh,
                onRequestPermission = onRequestPermission,
                onOpenBackendSettings = onOpenBackendSettings,
            )
            SimSelectionCard(
                selectedSim = selectedSim,
                allSimList = allSimList,
                simReadError = simReadError,
                onSelectSim = onSelectSim,
                onRefresh = onRefresh,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                GroupedSettingsItem(
                    title = stringResource(R.string.nav_ims_status),
                    first = true,
                    summary = stringResource(R.string.nav_ims_status_summary),
                    icon = Icons.Rounded.Info,
                    onClick = { showImsStatus = true },
                )
                GroupedSettingsItem(
                    title = stringResource(R.string.ims_configuration),
                    summary = stringResource(R.string.ims_configuration_summary),
                    icon = Icons.Rounded.Phone,
                    enabled = selectedSim != null,
                    onClick = onOpenImsConfig,
                )
                GroupedSettingsItem(
                    title = stringResource(R.string.system_network),
                    summary = stringResource(R.string.system_network_summary),
                    icon = Icons.Rounded.Public,
                    onClick = onOpenSystemNetwork,
                )
                GroupedSettingsItem(
                    title = stringResource(R.string.advanced_tools),
                    last = true,
                    summary = stringResource(R.string.advanced_tools_summary),
                    icon = Icons.Rounded.Build,
                    onClick = onOpenAdvancedTools,
                )
            }
            AutoRestoreCard(
                enabled = autoRestoreEnabled,
                onEnabledChange = onAutoRestoreEnabledChange,
            )
            // 留白随正文滚动，仅滚到底时将最后一张卡片托出导航栏，不制造固定底部色块。
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SimSelectionCard(
    selectedSim: SimSelection?,
    allSimList: List<SimSelection>,
    simReadError: String?,
    onSelectSim: (SimSelection) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.SimCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = stringResource(R.string.sim_card),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.refresh))
                }
            }
            if (simReadError != null) {
                Text(
                    text = stringResource(R.string.backend_sim_read_failed, simReadError),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (allSimList.isNotEmpty()) Text(
                    text = stringResource(R.string.backend_sim_last_good),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val actualSims = allSimList.filter { it.subId >= 0 }
            val displaySims = if (actualSims.size == 1) actualSims else allSimList
            Column(Modifier.selectableGroup()) {
                displaySims.forEach { sim ->
                    val isSelected = selectedSim?.subId == sim.subId
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        color = if (isSelected && actualSims.size > 1) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .then(if (actualSims.size > 1) Modifier.selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelectSim(sim) },
                            ) else Modifier),
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (actualSims.size > 1) RadioButton(selected = isSelected, onClick = null)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = if (sim.subId < 0) sim.showTitle else
                                        sim.displayName.trim().ifBlank { sim.carrierName.trim() }
                                            .ifBlank { stringResource(R.string.sim_card) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = if (sim.subId < 0) stringResource(R.string.nav_all_sims_hint)
                                        else stringResource(R.string.nav_sim_slot, sim.simSlotIndex + 1),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (allSimList.isEmpty() && simReadError == null) {
                Text(
                    text = stringResource(R.string.backend_no_sim_available),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
