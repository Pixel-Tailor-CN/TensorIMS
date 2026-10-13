package app.mystery0.ims.tensor.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.EmbeddedAutoStartPhase
import app.mystery0.ims.tensor.EmbeddedAutoStartState
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.embedded.WirelessAdbService
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.ui.wirelessAdbPermissionsToRequest

/** 只有用户明确启用时申请权限；普通前台恢复仅由控制器做无弹窗检查。 */
@Composable
fun EmbeddedAutoStartCard(enabled: Boolean, state: EmbeddedAutoStartState, backendStatus: BackendStatus, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (enableAfterPermission) { enableAfterPermission = false; onEnabledChange(true) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = { value ->
                enableAfterPermission = false
                if (value) {
                    WirelessAdbService.notificationAvailable(context)
                    val missing = wirelessAdbPermissionsToRequest(Build.VERSION.SDK_INT,
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                        Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
                    if (missing.isEmpty()) onEnabledChange(true)
                    else { enableAfterPermission = true; launcher.launch(missing.toTypedArray()) }
                } else onEnabledChange(false)
            }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.embedded_auto_start_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = enabled, onCheckedChange = null)
            }
            Text(stringResource(R.string.embedded_auto_start_description), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.embedded_auto_start_independent), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled) {
                val phase = state.forBackend(backendStatus).phase
                Text(stringResource(when (phase) {
                    EmbeddedAutoStartPhase.IDLE, EmbeddedAutoStartPhase.DISABLED -> R.string.embedded_auto_start_waiting
                    EmbeddedAutoStartPhase.CHECKING -> R.string.embedded_auto_start_checking
                    EmbeddedAutoStartPhase.READY -> R.string.embedded_auto_start_ready
                    EmbeddedAutoStartPhase.BUSY -> R.string.embedded_auto_start_busy
                    EmbeddedAutoStartPhase.RECOVERY -> R.string.embedded_auto_start_recovery
                    EmbeddedAutoStartPhase.BLOCKED -> R.string.embedded_auto_start_blocked
                    EmbeddedAutoStartPhase.NOTIFICATIONS_REQUIRED -> R.string.embedded_auto_start_notifications
                    EmbeddedAutoStartPhase.LOCAL_NETWORK_REQUIRED -> R.string.embedded_auto_start_local_network
                    EmbeddedAutoStartPhase.CREDENTIALS_REQUIRED -> R.string.embedded_auto_start_credentials
                    EmbeddedAutoStartPhase.CONNECTING -> R.string.embedded_auto_start_notification
                    EmbeddedAutoStartPhase.FAILED -> R.string.embedded_auto_start_failed
                    EmbeddedAutoStartPhase.CANCELLED -> R.string.embedded_auto_start_cancelled
                }), style = MaterialTheme.typography.bodySmall)
                if (phase == EmbeddedAutoStartPhase.FAILED) state.error?.let {
                    Text(stringResource(R.string.backend_error_details, it), style = MaterialTheme.typography.bodySmall)
                }
                if (phase in setOf(EmbeddedAutoStartPhase.NOTIFICATIONS_REQUIRED, EmbeddedAutoStartPhase.LOCAL_NETWORK_REQUIRED)) TextButton(onClick = {
                    context.startActivity(if (phase == EmbeddedAutoStartPhase.NOTIFICATIONS_REQUIRED) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.embedded_auto_start_permissions)) }
            }
        }
    }
}
