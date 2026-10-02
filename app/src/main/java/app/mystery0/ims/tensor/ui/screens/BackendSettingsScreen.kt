package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.embedded.WirelessAdbPhase
import app.mystery0.ims.tensor.embedded.WirelessAdbState
import app.mystery0.ims.tensor.ui.rememberWirelessAdbUiActions
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.ui.BackendAction
import app.mystery0.ims.tensor.ui.BackendActionState
import app.mystery0.ims.tensor.ui.backendUiActions
import app.mystery0.ims.tensor.ui.canConfirmPersistentRecovery
import app.mystery0.ims.tensor.ui.canConfirmModeChoice
import app.mystery0.ims.tensor.ui.components.BackendChoiceDialog
import app.mystery0.ims.tensor.ui.components.BackendErrorGuidance
import app.mystery0.ims.tensor.ui.components.BackendMigrationNotice
import app.mystery0.ims.tensor.ui.components.backendConnectionLabel
import app.mystery0.ims.tensor.ui.components.backendModeLabel

@Composable
fun BackendSettingsScreen(
    status: BackendStatus,
    action: BackendActionState,
    wirelessState: WirelessAdbState,
    busy: Boolean,
    showMigrationNotice: Boolean,
    canRecoverPersistentVolte: Boolean,
    canStartEmbeddedForRecovery: Boolean,
    canRequestOfficialPermissionForRecovery: Boolean,
    onChooseMode: (BackendMode) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onPair: () -> Boolean,
    onStartWireless: () -> Boolean,
    onCancelWireless: () -> Unit,
    onStartRoot: () -> Unit,
    onRecoverPersistentVolte: () -> Unit,
    onAcknowledgeMigration: () -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var confirmRecovery by rememberSaveable { mutableStateOf(false) }
    var showChoices by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    val operationBusy = busy || wirelessState.active
    val actions = backendUiActions(status, action.inProgress, operationBusy,
        recoveryStartAllowed = canStartEmbeddedForRecovery,
        recoveryPermissionAllowed = canRequestOfficialPermissionForRecovery)
    val wirelessActions = rememberWirelessAdbUiActions(wirelessState, actions.canStartEmbedded, onPair, onStartWireless)

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.backend_settings)) },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BackendSection {
                Text(stringResource(R.string.backend_selected_mode, backendModeLabel(status.mode)), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backend_connection, backendConnectionLabel(status.connection)))
                val identity = when (status.runtimeUid) {
                    0 -> "root (UID 0)"
                    2000 -> "shell (UID 2000)"
                    null -> stringResource(R.string.backend_unknown)
                    else -> "UID ${status.runtimeUid}"
                }
                Text(stringResource(R.string.backend_identity, identity))
                val authorization = when {
                    status.errorCode in setOf("PERMISSION_REQUIRED", "PERMISSION_DENIED") -> R.string.backend_permission_needed
                    status.connection in setOf(ConnectionState.READY, ConnectionState.BUSY) || canRecoverPersistentVolte -> R.string.backend_authorized
                    else -> R.string.backend_not_authorized
                }
                Text(stringResource(R.string.backend_authorization, stringResource(authorization)))
                Text(stringResource(R.string.backend_version, status.version ?: stringResource(R.string.backend_unknown)))
                BackendErrorGuidance(status)
                if (canRecoverPersistentVolte && status.connection == ConnectionState.RECOVERY_REQUIRED) {
                    Button(onClick = { confirmRecovery = true },
                        enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, operationBusy)) {
                        Text(stringResource(R.string.backend_restore_originals))
                    }
                }
                TextButton(onClick = { showChoices = true }, enabled = actions.canChooseMode) {
                    Text(stringResource(if (status.mode == BackendMode.UNSET) R.string.backend_choose_title else R.string.backend_switch))
                }
                if (!actions.canChooseMode) Text(stringResource(R.string.backend_switch_busy), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh, enabled = !operationBusy && !action.inProgress && status.connection !in setOf(ConnectionState.BUSY, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) {
                    Text(stringResource(R.string.backend_refresh_readonly))
                }
            }
            if (action.inProgress) {
                BackendSection {
                    CircularProgressIndicator()
                    Text(stringResource(when (action.action) {
                        BackendAction.RECOVER -> R.string.backend_restoring
                        BackendAction.PAIR -> R.string.backend_pairing
                        BackendAction.ROOT -> R.string.backend_starting_root
                        BackendAction.WIRELESS -> R.string.backend_starting_wireless
                        else -> R.string.backend_switching
                    }))
                    Text(stringResource(R.string.backend_action_in_progress), style = MaterialTheme.typography.bodySmall)
                }
            }
            action.notice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            action.error?.let { Text(stringResource(R.string.backend_action_failed, it), color = MaterialTheme.colorScheme.error) }
            when (status.mode) {
                BackendMode.UNSET -> Text(stringResource(R.string.backend_choose_description))
                BackendMode.OFFICIAL -> BackendSection {
                    Text(stringResource(R.string.backend_official_guidance))
                    Button(onClick = onRequestPermission, enabled = actions.canRequestOfficialPermission) {
                        Text(stringResource(R.string.request_permission))
                    }
                    TextButton(onClick = { uriHandler.openUri("https://shizuku.rikka.app/guide/setup/") }) {
                        Text(stringResource(R.string.backend_official_guide))
                    }
                }
                BackendMode.EMBEDDED -> {
                    BackendSection {
                        Text(stringResource(R.string.backend_wireless_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.wireless_ui_instructions), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.wireless_ui_privacy), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = wirelessActions.startPairing,
                            enabled = actions.canStartEmbedded && !wirelessActions.pending) {
                            Text(stringResource(R.string.wireless_ui_pair_and_open_settings))
                        }
                        Text(stringResource(R.string.wireless_ui_reconnect_help), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = wirelessActions.startConnect,
                            enabled = actions.canStartEmbedded && !wirelessActions.pending) {
                            Text(stringResource(R.string.wireless_ui_reconnect))
                        }
                        if (wirelessState.phase != WirelessAdbPhase.IDLE || wirelessState.active) {
                            if (wirelessState.active) CircularProgressIndicator()
                            Text(stringResource(when (wirelessState.phase) {
                                WirelessAdbPhase.IDLE -> R.string.wireless_ui_preparing
                                WirelessAdbPhase.SEARCHING_PAIRING -> R.string.wireless_ui_searching_pairing
                                WirelessAdbPhase.WAITING_CODE -> R.string.wireless_ui_waiting_code
                                WirelessAdbPhase.PAIRING -> R.string.wireless_ui_pairing
                                WirelessAdbPhase.SEARCHING_CONNECT -> R.string.wireless_ui_searching_connect
                                WirelessAdbPhase.STARTING -> R.string.wireless_ui_starting
                                WirelessAdbPhase.SUCCESS -> R.string.wireless_ui_success
                                WirelessAdbPhase.FAILED -> R.string.wireless_ui_failed
                            }))
                            wirelessState.error?.let {
                                Text(stringResource(R.string.backend_action_failed, it), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (wirelessState.active) {
                            Text(stringResource(R.string.wireless_ui_background_help), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = wirelessActions.reopenSettings) {
                                Text(stringResource(R.string.backend_open_wireless_settings))
                            }
                            TextButton(onClick = onCancelWireless) {
                                Text(stringResource(R.string.wireless_ui_cancel))
                            }
                        }
                    }
                    BackendSection {
                        Text(stringResource(R.string.backend_root_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.backend_root_help), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onStartRoot, enabled = actions.canStartEmbedded) { Text(stringResource(R.string.backend_start_root)) }
                    }
                }
            }
            if (showMigrationNotice) BackendMigrationNotice(onAcknowledgeMigration)
        }
    }
    if (confirmRecovery) AlertDialog(
        onDismissRequest = { confirmRecovery = false },
        title = { Text(stringResource(R.string.backend_restore_confirm)) },
        text = { Text(stringResource(R.string.backend_restore_description)) },
        confirmButton = { TextButton(onClick = {
            confirmRecovery = false
            onRecoverPersistentVolte()
        }, enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, operationBusy)) {
            Text(stringResource(R.string.backend_restore_originals))
        } },
        dismissButton = { TextButton(onClick = { confirmRecovery = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    if (showChoices) BackendChoiceDialog(
        currentMode = status.mode,
        enabled = actions.canChooseMode,
        showMigration = showMigrationNotice,
        onChoose = { mode ->
            showChoices = false
            if (status.mode == BackendMode.UNSET) onChooseMode(mode) else pendingMode = mode.name
        },
        onDismiss = { showChoices = false },
    )
    val target = pendingMode?.let { name -> BackendMode.entries.firstOrNull { it.name == name } }
    if (target != null) AlertDialog(
        onDismissRequest = { pendingMode = null },
        title = { Text(stringResource(R.string.backend_switch_title, backendModeLabel(target))) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backend_switch_description))
            if (!actions.canChooseMode) Text(stringResource(R.string.backend_switch_busy))
        } },
        confirmButton = { TextButton(onClick = {
            pendingMode = null
            onChooseMode(target)
        }, enabled = canConfirmModeChoice(status, target, action.inProgress, operationBusy)) {
            Text(stringResource(R.string.backend_switch_confirm))
        } },
        dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun BackendSection(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
