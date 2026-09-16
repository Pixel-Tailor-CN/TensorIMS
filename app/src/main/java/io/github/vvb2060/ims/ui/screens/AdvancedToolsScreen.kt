package io.github.vvb2060.ims.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.ImsCapabilityStatus
import io.github.vvb2060.ims.model.PersistentVolteState
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.ui.components.PersistentVolteCard
import io.github.vvb2060.ims.ui.components.SettingsListItem
import kotlinx.coroutines.launch

@Composable
fun AdvancedToolsScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    persistentVolteState: PersistentVolteState?,
    onLoadImsStatus: suspend (Int) -> ImsCapabilityStatus?,
    onEnablePersistentVolte: (Int) -> Unit,
    onRestorePersistentVolte: (Int) -> Unit,
    onRefreshPersistentVolte: () -> Unit,
    onRestartIms: (SimSelection) -> Unit,
    onResetConfiguration: (SimSelection) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentSubId by rememberUpdatedState(selectedSim?.subId)
    val currentShizukuStatus by rememberUpdatedState(shizukuStatus)
    val singleSimSelected = (selectedSim?.subId ?: -1) >= 0
    val canOperate = singleSimSelected && shizukuStatus == ShizukuStatus.READY && !isOperationInProgress

    var loadingImsStatus by remember { mutableStateOf(false) }
    var imsStatus by remember { mutableStateOf<ImsCapabilityStatus?>(null) }
    var showImsStatus by remember { mutableStateOf(false) }
    var imsStatusError by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSim?.subId, shizukuStatus) {
        imsStatus = null
        showImsStatus = false
        imsStatusError = false
        loadingImsStatus = false
    }

    fun loadImsStatus() {
        val subId = selectedSim?.subId ?: return
        if (subId < 0 || shizukuStatus != ShizukuStatus.READY || loadingImsStatus) return
        loadingImsStatus = true
        imsStatusError = false
        scope.launch {
            val result = onLoadImsStatus(subId)
            if (currentSubId == subId && currentShizukuStatus == ShizukuStatus.READY) {
                loadingImsStatus = false
                if (result != null) {
                    imsStatus = result
                    showImsStatus = true
                } else {
                    imsStatusError = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.advanced_tools)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
            if (!singleSimSelected) {
                Text(
                    text = stringResource(R.string.advanced_tools_single_sim_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 诊断与常规维护卡片
            Text(
                text = stringResource(R.string.diagnostics_and_maintenance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SettingsListItem(
                    title = stringResource(R.string.view_system_config),
                    summary = stringResource(R.string.view_system_config_summary),
                    enabled = canOperate && !loadingImsStatus,
                    showChevron = false,
                    onClick = { loadImsStatus() },
                )
                androidx.compose.material3.HorizontalDivider()
                SettingsListItem(
                    title = stringResource(R.string.restart_ims),
                    summary = stringResource(R.string.restart_ims_summary),
                    enabled = canOperate,
                    showChevron = false,
                    onClick = { confirmRestart = true },
                )
            }

            if (loadingImsStatus) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.loading))
                }
            }
            if (imsStatusError) {
                Text(
                    text = stringResource(R.string.load_system_config_error),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 持久化 VoLTE 卡片
            PersistentVolteCard(
                state = persistentVolteState?.takeIf { it.subId == selectedSim?.subId },
                singleSimSelected = singleSimSelected,
                shizukuReady = shizukuStatus == ShizukuStatus.READY,
                busy = isOperationInProgress,
                onEnable = { selectedSim?.let { onEnablePersistentVolte(it.subId) } },
                onRestore = { selectedSim?.let { onRestorePersistentVolte(it.subId) } },
                onRefresh = onRefreshPersistentVolte,
            )

            // 危险操作区域 (Danger Zone)
            Text(
                text = stringResource(R.string.danger_zone),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            androidx.compose.material3.OutlinedCard(
                colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SettingsListItem(
                    title = stringResource(R.string.reset_config),
                    summary = stringResource(R.string.danger_zone_desc),
                    enabled = canOperate,
                    showChevron = false,
                    onClick = { confirmReset = true },
                )
            }
            Spacer(modifier = Modifier.padding(bottom = 16.dp))
        }
    }

    if (showImsStatus && imsStatus != null) {
        ImsStatusDialog(
            status = imsStatus!!,
            simName = selectedSim?.showTitle.orEmpty(),
            isRefreshing = loadingImsStatus,
            onRefresh = { loadImsStatus() },
            onDismiss = { showImsStatus = false },
        )
    }

    if (confirmRestart) {
        ConfirmActionDialog(
            title = stringResource(R.string.restart_ims),
            message = stringResource(R.string.restart_ims_confirm, selectedSim?.showTitle.orEmpty()),
            confirmText = stringResource(R.string.restart_ims),
            onDismiss = { confirmRestart = false },
            onConfirm = {
                confirmRestart = false
                selectedSim?.let(onRestartIms)
            },
        )
    }

    if (confirmReset) {
        ConfirmActionDialog(
            title = stringResource(R.string.reset_config),
            message = stringResource(R.string.reset_config_confirm, selectedSim?.showTitle.orEmpty()),
            confirmText = stringResource(R.string.reset_config),
            onDismiss = { confirmReset = false },
            onConfirm = {
                confirmReset = false
                selectedSim?.let(onResetConfiguration)
            },
        )
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun ImsStatusDialog(
    status: ImsCapabilityStatus,
    simName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val regTechUnknown = stringResource(R.string.ims_reg_tech_unknown)
    val regTechDisplay = remember(status, regTechUnknown) {
        val techs = buildList {
            if (status.isVoWifiAvailable) add("WiFi")
            if (status.isVoNrAvailable) add("NR")
            if (status.isVolteAvailable) add("LTE")
        }
        when {
            techs.isNotEmpty() -> techs.joinToString(" / ")
            status.isRegistered && status.isNrSaAvailable -> "NR SA"
            status.isRegistered && status.isNrNsaAvailable -> "NR NSA"
            status.isRegistered -> regTechUnknown
            else -> "—"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.system_config_title))
                if (simName.isNotBlank()) {
                    Text(
                        text = simName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImsStatusRow(
                    label = "IMS",
                    isAvailable = status.isRegistered,
                    availableText = stringResource(R.string.ims_status_registered),
                    unavailableText = stringResource(R.string.ims_status_not_registered),
                )
                ImsTextRow(stringResource(R.string.ims_reg_tech), regTechDisplay)
                ImsStatusRow("VoLTE", status.isVolteAvailable)
                ImsStatusRow("VoWiFi", status.isVoWifiAvailable)
                ImsStatusRow("VoNR", status.isVoNrAvailable)
                ImsStatusRow(stringResource(R.string.vt), status.isVtAvailable)
                ImsStatusRow(stringResource(R.string.ims_cap_nr_nsa), status.isNrNsaAvailable)
                ImsStatusRow(stringResource(R.string.ims_cap_nr_sa), status.isNrSaAvailable)
                Text(
                    text = stringResource(R.string.ims_data_snapshot_note),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh))
                }
            }
        },
    )
}

@Composable
private fun ImsStatusRow(
    label: String,
    isAvailable: Boolean,
    availableText: String = stringResource(R.string.status_available),
    unavailableText: String = stringResource(R.string.status_unavailable),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
        )
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            color = if (isAvailable) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                text = if (isAvailable) availableText else unavailableText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ImsTextRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
