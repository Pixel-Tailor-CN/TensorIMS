package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.ConfigRecovery
import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.FeatureValue
import app.mystery0.ims.tensor.model.FeatureValueType
import app.mystery0.ims.tensor.model.ImsEditorState

@Composable
fun TargetFeatureSection(
    title: String,
    features: List<Feature>,
    state: ImsEditorState,
    enabled: Boolean,
    onEdit: (Feature, FeatureValue) -> Unit,
    onStringClick: (Feature) -> Unit,
    onExplainReset: () -> Unit,
) {
    if (features.isEmpty()) return
    Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        features.forEachIndexed { index, feature ->
            val value = state.values[feature]?.data
            val supported = feature in state.supported
            val resetOnly = state.recovery(feature) == ConfigRecovery.RESET_REQUIRED
            var mixedMenu by remember(feature) { mutableStateOf(false) }
            val bool = value as? Boolean
            fun change(checked: Boolean) = onEdit(feature, FeatureValue(checked, FeatureValueType.BOOLEAN))
            val rowModifier = when {
                !supported -> Modifier
                feature.valueType == FeatureValueType.STRING -> Modifier.clickable(enabled = enabled) { onStringClick(feature) }
                resetOnly -> Modifier
                bool == null -> Modifier.clickable(enabled = enabled) { mixedMenu = true }
                else -> Modifier.toggleable(value = bool, enabled = enabled, role = Role.Switch, onValueChange = ::change)
            }
            ListItem(modifier = rowModifier,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(feature.showTitleRes)) },
                supportingContent = { Column {
                    Text(if (feature.valueType == FeatureValueType.STRING && value is String && value.isNotBlank()) value
                        else stringResource(feature.showDescriptionRes))
                    if (feature in state.edits) Text(stringResource(R.string.ims_pending), color = MaterialTheme.colorScheme.primary)
                    if (supported && state.recovery(feature) == ConfigRecovery.SYSTEM_DEFAULT) {
                        Text(stringResource(R.string.ims_system_default_hint), style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = enabled, onClick = { change(false) }) {
                            Text(stringResource(R.string.ims_restore_system_defaults))
                        }
                    }
                } },
                trailingContent = {
                    when {
                        !supported -> Text(stringResource(R.string.ims_unknown), style = MaterialTheme.typography.labelSmall)
                        feature.valueType == FeatureValueType.STRING -> TextButton(onClick = { onStringClick(feature) }, enabled = enabled) {
                            Text(stringResource(R.string.ims_edit))
                        }
                        resetOnly -> TextButton(enabled = enabled, onClick = {
                            when {
                                feature in state.edits && state.common[feature]?.data == false -> change(false)
                                bool != true -> change(true)
                                else -> onExplainReset()
                            }
                        }) { Text(stringResource(when {
                            feature in state.edits && state.common[feature]?.data == false -> R.string.ims_undo
                            bool == true -> R.string.ims_reset_to_restore
                            else -> R.string.ims_enable
                        })) }
                        bool != null -> Switch(checked = bool, enabled = enabled, onCheckedChange = null)
                        else -> Box {
                            TextButton(onClick = { mixedMenu = true }, enabled = enabled) { Text(stringResource(R.string.ims_mixed)) }
                            DropdownMenu(expanded = mixedMenu, onDismissRequest = { mixedMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.ims_enable)) }, onClick = { mixedMenu = false; change(true) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.ims_disable)) }, onClick = { mixedMenu = false; change(false) })
                            }
                        }
                    }
                },
            )
            if (index != features.lastIndex) HorizontalDivider()
        }
    }
}
