package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.CarrierConfigChoices
import app.mystery0.ims.tensor.model.Feature

@Composable
fun CarrierConfigChoiceDialog(
    feature: Feature,
    current: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val choices = CarrierConfigChoices.selectableValues(feature)
    // null 表示各卡不同；保留未知系统整数，不自动映射成第一个选项。
    var selected by remember(feature, current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(feature.showTitleRes)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(feature.showDescriptionRes))
                Text(
                    text = stringResource(R.string.ims_choice_current, carrierConfigChoiceLabel(feature, current)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current != null && current !in choices) {
                    Text(
                        text = stringResource(R.string.ims_choice_unknown_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (feature == Feature.USSD_METHOD) {
                    Text(
                        text = stringResource(R.string.ussd_method_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.selectableGroup()) {
                    choices.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = selected == choice,
                                    role = Role.RadioButton,
                                    onClick = { selected = choice },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == choice, onClick = null)
                            Column(
                                Modifier.weight(1f).padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(carrierConfigChoiceLabel(feature, choice))
                                if (feature == Feature.USSD_METHOD) {
                                    Text(
                                        text = stringResource(when (choice) {
                                            0 -> R.string.ussd_cs_preferred_desc
                                            1 -> R.string.ussd_ims_preferred_desc
                                            2 -> R.string.ussd_cs_only_desc
                                            else -> R.string.ussd_ims_only_desc
                                        }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.ims_choice_apply_reset_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected?.let { CarrierConfigChoices.isSelectable(feature, it) } == true,
                onClick = { selected?.takeIf { CarrierConfigChoices.isSelectable(feature, it) }?.let(onConfirm) },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun carrierConfigChoiceLabel(feature: Feature, value: Int?): String {
    if (value == null) return stringResource(R.string.ims_mixed)
    val label = when (feature) {
        Feature.VOWIFI_NAME_FORMAT -> when (value) {
            0 -> R.string.vowifi_format_carrier_only
            1 -> R.string.vowifi_format_wifi_calling
            4 -> R.string.vowifi_format_wifi
            6 -> R.string.vowifi_format_vowifi
            else -> null
        }
        Feature.USSD_METHOD -> when (value) {
            0 -> R.string.ussd_cs_preferred
            1 -> R.string.ussd_ims_preferred
            2 -> R.string.ussd_cs_only
            3 -> R.string.ussd_ims_only
            else -> null
        }
        else -> null
    }
    return label?.let { stringResource(it) } ?: stringResource(R.string.ims_choice_other, value)
}
