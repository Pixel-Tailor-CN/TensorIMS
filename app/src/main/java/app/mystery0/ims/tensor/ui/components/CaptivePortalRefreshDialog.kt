package app.mystery0.ims.tensor.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mystery0.ims.tensor.R

@Composable
fun CaptivePortalRefreshDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backend_portal_discard_title)) },
        text = { Text(stringResource(R.string.backend_portal_discard_description)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.refresh)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
