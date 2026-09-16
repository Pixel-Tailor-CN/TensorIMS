package io.github.vvb2060.ims.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SystemInfo

@Composable
fun DeviceStatusCard(
    systemInfo: SystemInfo,
    shizukuStatus: ShizukuStatus,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = systemInfo.deviceModel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = systemInfo.androidVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = shizukuStatusLabel(shizukuStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (shizukuStatus) {
                        ShizukuStatus.NOT_RUNNING, ShizukuStatus.NEED_UPDATE -> MaterialTheme.colorScheme.error
                        ShizukuStatus.NO_PERMISSION -> MaterialTheme.colorScheme.tertiary
                        ShizukuStatus.READY -> MaterialTheme.colorScheme.primary
                        ShizukuStatus.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showDetails = true }) {
                    Text(stringResource(R.string.device_details))
                }
            }
            when (shizukuStatus) {
                ShizukuStatus.NO_PERMISSION -> {
                    TextButton(onClick = onRequestShizukuPermission) {
                        Text(stringResource(R.string.request_permission))
                    }
                }

                ShizukuStatus.NOT_RUNNING, ShizukuStatus.NEED_UPDATE -> {
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.refresh))
                    }
                }

                ShizukuStatus.CHECKING, ShizukuStatus.READY -> Unit
            }
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
private fun shizukuStatusLabel(status: ShizukuStatus): String {
    val statusText = when (status) {
        ShizukuStatus.CHECKING -> stringResource(R.string.shizuku_checking)
        ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
        ShizukuStatus.NO_PERMISSION -> stringResource(R.string.shizuku_no_permission)
        ShizukuStatus.READY -> stringResource(R.string.shizuku_ready)
        ShizukuStatus.NEED_UPDATE -> stringResource(R.string.shizuku_need_update)
    }
    return stringResource(R.string.shizuku_status, statusText)
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
