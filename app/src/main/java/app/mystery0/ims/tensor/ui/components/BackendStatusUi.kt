package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState
import app.mystery0.ims.tensor.ui.BackendPrimaryAction
import app.mystery0.ims.tensor.ui.backendUiActions

@Composable
fun backendModeLabel(mode: BackendMode): String = stringResource(when (mode) {
    BackendMode.UNSET -> R.string.backend_mode_unset
    BackendMode.OFFICIAL -> R.string.backend_mode_official
    BackendMode.EMBEDDED -> R.string.backend_mode_embedded
})

@Composable
fun backendConnectionLabel(connection: ConnectionState): String = stringResource(when (connection) {
    ConnectionState.DISCONNECTED -> R.string.backend_disconnected
    ConnectionState.CONNECTING -> R.string.backend_connecting
    ConnectionState.READY -> R.string.backend_ready
    ConnectionState.BUSY -> R.string.backend_busy
    ConnectionState.BLOCKED -> R.string.backend_blocked
    ConnectionState.SWITCHING -> R.string.backend_switching
    ConnectionState.RECOVERY_REQUIRED -> R.string.backend_recovery
})

@Composable
fun BackendErrorGuidance(status: BackendStatus) {
    val resource = when {
        status.connection == ConnectionState.RECOVERY_REQUIRED -> R.string.backend_recovery_guidance
        else -> when (status.errorCode) {
            "OFFICIAL_UNAVAILABLE", "NOT_RUNNING" -> if (status.mode == BackendMode.OFFICIAL) R.string.backend_error_official else null
            "PERMISSION_REQUIRED", "PERMISSION_DENIED" -> R.string.backend_error_permission
            "PAIRING_REQUIRED", "ADB_REVOKED" -> R.string.backend_error_pairing
            "AUTH_FAILED", "VERSION_MISMATCH" -> R.string.backend_error_auth
            "DELEGATION_BUSY" -> R.string.backend_error_delegation
            "OPERATION_INDETERMINATE", "CLEANUP_FAILED", "ORPHANED_SERVICE" -> R.string.backend_recovery_guidance
            "UNSUPPORTED_CAPABILITY" -> R.string.backend_error_unsupported
            else -> null
        }
    }
    resource?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    status.message?.takeIf { status.errorCode != "MODE_UNSET" }?.let {
        Text(stringResource(R.string.backend_error_details, it), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BackendStatusBanner(
    status: BackendStatus,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val actions = backendUiActions(status, businessBusy = busy)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (status.connection == ConnectionState.RECOVERY_REQUIRED) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.backend_selected_mode, backendModeLabel(status.mode)), style = MaterialTheme.typography.titleSmall)
            Text(backendConnectionLabel(status.connection), style = MaterialTheme.typography.bodyMedium)
            if (status.mode == BackendMode.UNSET) Text(stringResource(R.string.backend_choose_description), style = MaterialTheme.typography.bodySmall)
            val label = when (actions.primary) {
                BackendPrimaryAction.CHOOSE_MODE -> R.string.backend_choose_title
                BackendPrimaryAction.AUTHORIZE_OFFICIAL -> R.string.request_permission
                BackendPrimaryAction.REFRESH -> R.string.backend_refresh_readonly
                BackendPrimaryAction.WAIT -> R.string.backend_wait
                BackendPrimaryAction.REVIEW_RECOVERY -> R.string.backend_review_recovery
                BackendPrimaryAction.OPEN_SETTINGS -> R.string.backend_setup
            }
            TextButton(
                enabled = actions.primary != BackendPrimaryAction.WAIT,
                onClick = {
                    when (actions.primary) {
                        BackendPrimaryAction.AUTHORIZE_OFFICIAL -> onRequestPermission()
                        BackendPrimaryAction.REFRESH -> onRefresh()
                        BackendPrimaryAction.WAIT -> Unit
                        else -> onOpenSettings()
                    }
                },
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
fun BackendChoiceDialog(
    currentMode: BackendMode,
    enabled: Boolean,
    showMigration: Boolean,
    onChoose: (BackendMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backend_choose_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backend_choose_description))
                if (showMigration) Text(stringResource(R.string.backend_migration_short), style = MaterialTheme.typography.bodySmall)
                // 两个同等样式的显式按钮，不预选、不因检测到任一服务而自动选择。
                listOf(BackendMode.OFFICIAL, BackendMode.EMBEDDED).forEach { mode ->
                    FilledTonalButton(
                        onClick = { onChoose(mode) },
                        enabled = enabled && currentMode != mode,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(backendModeLabel(mode), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (mode == BackendMode.OFFICIAL) R.string.backend_official_description else R.string.backend_embedded_description),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (!enabled) Text(stringResource(R.string.backend_switch_busy))
                if (showMigration) {
                    Text(stringResource(R.string.backend_migration_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.backend_migration_description), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
fun BackendMigrationNotice(onAcknowledge: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backend_migration_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backend_migration_description), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.backend_migration_understood)) }
    }
}
