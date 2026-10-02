package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.model.SystemInfo

@Composable
fun DeviceStatusCard(
    systemInfo: SystemInfo,
    backendStatus: BackendStatus,
    busy: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBackendSettings: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 设备元信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = systemInfo.deviceModel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = systemInfo.androidVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showDetails = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.device_details))
                }
            }

            // 显示实际所选后端，选择与授权状态保持区分。
            BackendStatusBanner(
                status = backendStatus,
                busy = busy,
                onRefresh = onRefresh,
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenBackendSettings,
            )
        }
    }

    if (showDetails) {
        DeviceDetailsDialog(
            systemInfo = systemInfo,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun DeviceDetailsDialog(
    systemInfo: SystemInfo,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_version, systemInfo.appVersionName))
                Text(stringResource(R.string.device_model, systemInfo.deviceModel))
                Text(stringResource(R.string.android_version, systemInfo.androidVersion))
                Text(stringResource(R.string.system_build_version, systemInfo.systemVersion))
                Text(stringResource(R.string.security_patch_version, systemInfo.securityPatchVersion))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                uriHandler.openUri("https://github.com/Pixel-Tailor-CN/TensorIMS")
            }) {
                Text("GitHub")
            }
        },
    )
}
