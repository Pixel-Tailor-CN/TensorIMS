package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.ImsEditorState
import app.mystery0.ims.tensor.model.TikTokNetworkFix

/** 字符串目标使用专用确认入口，避免任意国家码输入或误导性的关闭开关。 */
@Composable
fun TikTokFixCard(
    state: ImsEditorState,
    enabled: Boolean,
    onEnable: () -> Unit,
    onUndo: () -> Unit,
    onExplainReset: () -> Unit,
) {
    var confirm by remember(state.subId) { mutableStateOf(false) }
    val supported = Feature.TIKTOK_NETWORK_FIX in state.supported
    val pending = Feature.TIKTOK_NETWORK_FIX in state.edits
    val iso = state.values[Feature.TIKTOK_NETWORK_FIX]?.data as? String
    val numeric = iso?.let(TikTokNetworkFix::isNumericIso) == true

    Text(stringResource(R.string.tiktok_network_fix),
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tiktok_network_fix_desc), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(when {
                !supported -> R.string.tiktok_network_fix_unavailable
                pending -> R.string.ims_pending
                numeric -> R.string.tiktok_network_fix_configured
                else -> R.string.tiktok_network_fix_not_configured
            }), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary)
            if (supported && !iso.isNullOrBlank()) {
                Text(stringResource(R.string.tiktok_network_fix_iso, iso), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = enabled && supported, onClick = {
                when {
                    pending -> onUndo()
                    numeric -> onExplainReset()
                    else -> confirm = true
                }
            }) {
                Text(stringResource(when {
                    pending -> R.string.ims_undo
                    numeric -> R.string.ims_reset_to_restore
                    else -> R.string.ims_enable
                }))
            }
        }
    }
    if (confirm && enabled && supported) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.tiktok_network_fix)) },
        text = { Text(stringResource(R.string.tiktok_network_fix_confirmation)) },
        confirmButton = { TextButton(onClick = { confirm = false; onEnable() }) {
            Text(stringResource(R.string.ims_enable))
        } },
        dismissButton = { TextButton(onClick = { confirm = false }) {
            Text(stringResource(android.R.string.cancel))
        } })
}
