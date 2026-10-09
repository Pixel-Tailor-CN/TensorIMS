package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.EmbeddedIdleStopPolicy

@Composable
fun EmbeddedIdleStopCard(
    enabled: Boolean,
    minutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.embedded_idle_title), style = MaterialTheme.typography.titleMedium)
                    Text(if (enabled) stringResource(R.string.embedded_idle_on, minutes)
                        else stringResource(R.string.embedded_idle_off),
                        style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = enabled, onCheckedChange = null)
            }
            if (enabled) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) {
                        input = minutes.toString()
                        editing = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.embedded_idle_delay), Modifier.weight(1f))
                    Text(stringResource(R.string.embedded_idle_minutes, minutes), color = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.embedded_idle_timing_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.embedded_idle_restart_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editing) {
        val parsed = EmbeddedIdleStopPolicy.parseMinutes(input)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.embedded_idle_delay)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.embedded_idle_input_help))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.embedded_idle_input_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsed == null,
                        supportingText = { if (parsed == null) Text(stringResource(R.string.embedded_idle_input_error)) },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = parsed != null, onClick = {
                    parsed?.let(onMinutesChange)
                    editing = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
