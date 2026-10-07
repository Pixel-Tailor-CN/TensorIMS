package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedButton
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
    onOpenImsConfig: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var confirmRecovery by rememberSaveable { mutableStateOf(false) }
    var showChoices by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val operationBusy = busy || wirelessState.active
    val actions = backendUiActions(status, action.inProgress, operationBusy,
        recoveryStartAllowed = canStartEmbeddedForRecovery,
        recoveryPermissionAllowed = canRequestOfficialPermissionForRecovery)
    val wirelessActions = rememberWirelessAdbUiActions(wirelessState, actions.canStartEmbedded, onPair, onStartWireless)
    val recovering = status.connection == ConnectionState.RECOVERY_REQUIRED
    val locked = operationBusy || action.inProgress || wirelessActions.pending || status.connection in
        setOf(ConnectionState.BUSY, ConnectionState.SWITCHING, ConnectionState.CONNECTING)
    val ready = status.isReady && !locked && !recovering

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
                Text(if (!recovering && (wirelessState.active || wirelessActions.pending))
                    stringResource(R.string.connection_progress) else backendConnectionLabel(status.connection),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (recovering) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (ready) {
                    Text(stringResource(R.string.connection_ready_help), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenImsConfig, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connection_open_ims))
                    }
                    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connection_home))
                    }
                }
                if (showDetails) {
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
                }
                if (recovering || (!locked && status.errorCode != null)) BackendErrorGuidance(status)
                if (canRecoverPersistentVolte && status.connection == ConnectionState.RECOVERY_REQUIRED) {
                    Button(onClick = { confirmRecovery = true },
                        enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, operationBusy || wirelessActions.pending)) {
                        Text(stringResource(R.string.backend_restore_originals))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(stringResource(if (showDetails) R.string.connection_hide_details else R.string.connection_details))
                    }
                    TextButton(onClick = { showChoices = true }, enabled = actions.canChooseMode && !wirelessActions.pending) {
                        Text(stringResource(if (status.mode == BackendMode.UNSET) R.string.backend_choose_title else R.string.backend_switch))
                    }
                    TextButton(onClick = onRefresh, enabled = !locked) {
                        Text(stringResource(R.string.backend_refresh_readonly))
                    }
                }
                if (!actions.canChooseMode) Text(stringResource(R.string.backend_switch_busy), style = MaterialTheme.typography.bodySmall)
            }
            if (locked && !wirelessState.active) {
                BackendSection {
                    CircularProgressIndicator()
                    Text(stringResource(when (action.action) {
                        BackendAction.RECOVER -> R.string.backend_restoring
                        BackendAction.PAIR -> R.string.backend_pairing
                        BackendAction.ROOT -> R.string.backend_starting_root
                        BackendAction.WIRELESS -> R.string.backend_starting_wireless
                        else -> if (status.connection == ConnectionState.SWITCHING) R.string.backend_switching else R.string.connection_working
                    }))
                    Text(stringResource(R.string.backend_action_in_progress), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!ready) action.notice?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            if (!ready) action.error?.let { Text(stringResource(R.string.backend_action_failed, it), color = MaterialTheme.colorScheme.error) }
            when (status.mode) {
                BackendMode.UNSET -> Text(stringResource(R.string.backend_choose_description))
                BackendMode.OFFICIAL -> if (!ready) BackendSection {
                    Text(stringResource(R.string.backend_official_guidance))
                    Button(onClick = onRequestPermission, enabled = actions.canRequestOfficialPermission) {
                        Text(stringResource(R.string.request_permission))
                    }
                    TextButton(onClick = { uriHandler.openUri("https://shizuku.rikka.app/guide/setup/") }) {
                        Text(stringResource(R.string.backend_official_guide))
                    }
                }
                BackendMode.EMBEDDED -> if (!ready) EmbeddedConnectionContent(
                    state = wirelessState,
                    recovering = recovering,
                    locked = locked,
                    canStart = actions.canStartEmbedded && !wirelessActions.pending,
                    onConnect = wirelessActions.startConnect,
                    onPair = wirelessActions.startPairing,
                    onSettings = wirelessActions.reopenSettings,
                    onCancel = onCancelWireless,
                    onRoot = onStartRoot,
                )
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
        }, enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, operationBusy || wirelessActions.pending)) {
            Text(stringResource(R.string.backend_restore_originals))
        } },
        dismissButton = { TextButton(onClick = { confirmRecovery = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
    if (showChoices) BackendChoiceDialog(
        currentMode = status.mode,
        enabled = actions.canChooseMode && !wirelessActions.pending,
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
        }, enabled = canConfirmModeChoice(status, target, action.inProgress, operationBusy || wirelessActions.pending)) {
            Text(stringResource(R.string.backend_switch_confirm))
        } },
        dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun BackendSection(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
