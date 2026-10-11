package app.mystery0.ims.tensor.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R

/** 命令只来自运行时已登记挑战的启动任务，关闭对话框即取消等待。 */
@Composable
fun ComputerAdbDialog(command: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.computer_adb_title)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.computer_adb_steps))
                SelectionContainer {
                    Text(command, modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Text(stringResource(R.string.computer_adb_wait), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, command)
                    }
                    try {
                        context.startActivity(Intent.createChooser(sendIntent, title))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.computer_adb_share_unavailable, Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(context, R.string.computer_adb_share_unavailable, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.computer_adb_send)) }
                TextButton(onClick = {
                    val clip = ClipData.newPlainText(title, command)
                    // Android 13+ 隐藏启动命令的剪贴板预览，减少其他可见界面误读命令的机会。
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                }) { Text(stringResource(R.string.computer_adb_copy)) }
                TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            }
        },
        dismissButton = null,
    )
}
