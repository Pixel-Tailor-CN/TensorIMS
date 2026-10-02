package app.mystery0.ims.tensor.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.ui.BackendAction
import app.mystery0.ims.tensor.ui.BackendActionState
import app.mystery0.ims.tensor.ui.backendUiActions
import app.mystery0.ims.tensor.ui.canConfirmPersistentRecovery
import app.mystery0.ims.tensor.ui.canConfirmModeChoice
import app.mystery0.ims.tensor.ui.parseAdbPort
import app.mystery0.ims.tensor.ui.validPairingCode
import app.mystery0.ims.tensor.ui.components.BackendChoiceDialog
import app.mystery0.ims.tensor.ui.components.BackendErrorGuidance
import app.mystery0.ims.tensor.ui.components.BackendMigrationNotice
import app.mystery0.ims.tensor.ui.components.backendConnectionLabel
import app.mystery0.ims.tensor.ui.components.backendModeLabel

@Composable
fun BackendSettingsScreen(
    status: BackendStatus,
    action: BackendActionState,
    busy: Boolean,
    showMigrationNotice: Boolean,
    canRecoverPersistentVolte: Boolean,
    canStartEmbeddedForRecovery: Boolean,
    canRequestOfficialPermissionForRecovery: Boolean,
    onChooseMode: (BackendMode) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onPair: (String, String) -> Unit,
    onStartWireless: (String) -> Unit,
    onStartRoot: () -> Unit,
    onRecoverPersistentVolte: () -> Unit,
    onAcknowledgeMigration: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var confirmRecovery by rememberSaveable { mutableStateOf(false) }
    var showChoices by rememberSaveable { mutableStateOf(false) }
    var pendingMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var connectionPort by rememberSaveable { mutableStateOf("") }
    // 配对码不使用 rememberSaveable，不落 Bundle、首选项或日志；提交后立即清空输入。
    var pairingCode by remember { mutableStateOf("") }
    val actions = backendUiActions(status, action.inProgress, busy,
        recoveryStartAllowed = canStartEmbeddedForRecovery,
        recoveryPermissionAllowed = canRequestOfficialPermissionForRecovery)

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
                        enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, busy)) {
                        Text(stringResource(R.string.backend_restore_originals))
                    }
                }
                TextButton(onClick = { showChoices = true }, enabled = actions.canChooseMode) {
                    Text(stringResource(if (status.mode == BackendMode.UNSET) R.string.backend_choose_title else R.string.backend_switch))
                }
                if (!actions.canChooseMode) Text(stringResource(R.string.backend_switch_busy), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh, enabled = !action.inProgress && status.connection !in setOf(ConnectionState.BUSY, ConnectionState.SWITCHING, ConnectionState.CONNECTING)) {
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
                        Text(stringResource(R.string.backend_wireless_help), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            // 只响应明确点击；不写入全局 ADB 设置、不接受设备位置等额外权限。
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, R.string.backend_settings_unavailable, Toast.LENGTH_LONG).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, R.string.backend_settings_unavailable, Toast.LENGTH_LONG).show()
                            }
                        }, enabled = !action.inProgress) { Text(stringResource(R.string.backend_open_wireless_settings)) }
                        BackendPortField(pairingPort, { pairingPort = it }, R.string.backend_pair_port, actions.canStartEmbedded)
                        OutlinedTextField(
                            value = pairingCode,
                            onValueChange = { pairingCode = it },
                            label = { Text(stringResource(R.string.backend_pair_code)) },
                            enabled = actions.canStartEmbedded,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pairingCode.isNotEmpty() && !validPairingCode(pairingCode),
                            supportingText = { if (pairingCode.isNotEmpty() && !validPairingCode(pairingCode)) Text(stringResource(R.string.backend_code_error)) },
                        )
                        Button(onClick = {
                            val code = pairingCode
                            pairingCode = ""
                            onPair(pairingPort, code)
                        }, enabled = actions.canStartEmbedded && parseAdbPort(pairingPort) != null && validPairingCode(pairingCode)) {
                            Text(stringResource(R.string.backend_pair))
                        }
                        Spacer(Modifier.height(8.dp))
                        BackendPortField(connectionPort, { connectionPort = it }, R.string.backend_connection_port, actions.canStartEmbedded)
                        Button(onClick = { onStartWireless(connectionPort) }, enabled = actions.canStartEmbedded && parseAdbPort(connectionPort) != null) {
                            Text(stringResource(R.string.backend_connect_wireless))
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
        }, enabled = canConfirmPersistentRecovery(status, canRecoverPersistentVolte, action.inProgress, busy)) {
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
        }, enabled = canConfirmModeChoice(status, target, action.inProgress, busy)) {
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

@Composable
private fun BackendPortField(value: String, onChange: (String) -> Unit, label: Int, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = value.isNotEmpty() && parseAdbPort(value) == null,
        supportingText = { if (value.isNotEmpty() && parseAdbPort(value) == null) Text(stringResource(R.string.backend_port_error)) },
    )
}
