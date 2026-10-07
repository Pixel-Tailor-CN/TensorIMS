package app.mystery0.ims.tensor.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.embedded.WirelessAdbPhase
import app.mystery0.ims.tensor.embedded.WirelessAdbState

/** 只呈现当前允许的下一步，所有启动仍经过既有权限和运行时门禁。 */
@Composable
internal fun EmbeddedConnectionContent(
    state: WirelessAdbState,
    recovering: Boolean,
    locked: Boolean,
    canStart: Boolean,
    onConnect: () -> Unit,
    onPair: () -> Unit,
    onSettings: () -> Unit,
    onCancel: () -> Unit,
    onRoot: () -> Unit,
) {
    var showRoot by rememberSaveable { mutableStateOf(false) }
    when {
        state.active -> BackendSection {
            Text(stringResource(R.string.connection_progress), style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator()
            Text(stringResource(when (state.phase) {
                WirelessAdbPhase.SEARCHING_PAIRING -> R.string.wireless_ui_searching_pairing
                WirelessAdbPhase.WAITING_CODE -> R.string.connection_waiting_code
                WirelessAdbPhase.PAIRING -> R.string.wireless_ui_pairing
                WirelessAdbPhase.SEARCHING_CONNECT -> R.string.wireless_ui_searching_connect
                WirelessAdbPhase.STARTING -> R.string.wireless_ui_starting
                else -> R.string.wireless_ui_preparing
            }), style = MaterialTheme.typography.bodyLarge)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.wireless_ui_background_help), style = MaterialTheme.typography.bodySmall)
            if (state.phase in setOf(WirelessAdbPhase.SEARCHING_PAIRING, WirelessAdbPhase.WAITING_CODE)) {
                TextButton(onClick = onSettings) { Text(stringResource(R.string.backend_open_wireless_settings)) }
            }
            // 此按钮只存在于活动无线会话，不能用来取消业务写入或模式切换。
            TextButton(onClick = onCancel) { Text(stringResource(R.string.wireless_ui_cancel)) }
        }
        locked -> Unit
        recovering && !canStart -> Unit
        else -> {
            BackendSection {
                Text(stringResource(if (recovering) R.string.connection_recovery_start else R.string.backend_wireless_title),
                    style = MaterialTheme.typography.titleMedium)
                if (!recovering && state.phase == WirelessAdbPhase.FAILED) {
                    Text(stringResource(if (state.pairedInSession) R.string.connection_paired_failed else R.string.connection_failed),
                        style = MaterialTheme.typography.bodyLarge)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Text(stringResource(R.string.connection_connect_help), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onConnect, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(when {
                        recovering -> R.string.connection_recovery_start
                        state.phase == WirelessAdbPhase.FAILED -> R.string.connection_retry
                        else -> R.string.connection_connect
                    }))
                }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.backend_open_wireless_settings)) }
            }
            // 恢复期间优先提供受控重连；只有安全门禁允许时才保留显式配对入口。
            BackendSection {
                Text(stringResource(R.string.connection_pair_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.connection_pair_steps), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onPair, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wireless_ui_pair_and_open_settings))
                }
                Text(stringResource(R.string.wireless_ui_privacy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showRoot = !showRoot }) {
                Text(stringResource(if (showRoot) R.string.connection_hide_root else R.string.connection_other_methods))
            }
            if (showRoot) BackendSection {
                Text(stringResource(R.string.backend_root_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backend_root_help), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRoot, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backend_start_root))
                }
            }
        }
    }
}
