package io.github.vvb2060.ims.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.model.SystemInfo
import io.github.vvb2060.ims.ui.components.AutoRestoreCard
import io.github.vvb2060.ims.ui.components.DeviceStatusCard
import io.github.vvb2060.ims.ui.components.SettingsListItem

@Composable
fun HomeScreen(
    systemInfo: SystemInfo,
    shizukuStatus: ShizukuStatus,
    allSimList: List<SimSelection>,
    selectedSim: SimSelection?,
    autoRestoreEnabled: Boolean,
    onSelectSim: (SimSelection) -> Unit,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenImsConfig: () -> Unit,
    onOpenSystemNetwork: () -> Unit,
    onOpenAdvancedTools: () -> Unit,
    onAutoRestoreEnabledChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            DeviceStatusCard(
                systemInfo = systemInfo,
                shizukuStatus = shizukuStatus,
                onRefresh = onRefresh,
                onRequestShizukuPermission = onRequestShizukuPermission,
            )
            SimSelectionCard(
                selectedSim = selectedSim,
                allSimList = allSimList,
                onSelectSim = onSelectSim,
                onRefresh = onRefresh,
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SettingsListItem(
                    title = stringResource(R.string.ims_configuration),
                    summary = stringResource(R.string.ims_configuration_summary),
                    icon = Icons.Rounded.Phone,
                    enabled = selectedSim != null,
                    onClick = onOpenImsConfig,
                )
                SettingsListItem(
                    title = stringResource(R.string.system_network),
                    summary = stringResource(R.string.system_network_summary),
                    icon = Icons.Rounded.Public,
                    onClick = onOpenSystemNetwork,
                )
                SettingsListItem(
                    title = stringResource(R.string.advanced_tools),
                    summary = stringResource(R.string.advanced_tools_summary),
                    icon = Icons.Rounded.Build,
                    onClick = onOpenAdvancedTools,
                )
            }
            AutoRestoreCard(
                enabled = autoRestoreEnabled,
                onEnabledChange = onAutoRestoreEnabledChange,
            )
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SimSelectionCard(
    selectedSim: SimSelection?,
    allSimList: List<SimSelection>,
    onSelectSim: (SimSelection) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            allSimList.forEach { sim ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedSim?.subId == sim.subId,
                            onClick = { onSelectSim(sim) },
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedSim?.subId == sim.subId,
                        onClick = null,
                    )
                    Text(
                        text = sim.showTitle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (allSimList.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_sim_available),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
