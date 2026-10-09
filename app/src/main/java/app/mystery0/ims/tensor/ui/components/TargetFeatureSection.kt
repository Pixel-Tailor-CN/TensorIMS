package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    SettingsSection(
        title = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        features.forEachIndexed { index, feature ->
            val value = state.values[feature]?.data
            val supported = feature in state.supported
            val resetOnly = state.recovery(feature) == ConfigRecovery.RESET_REQUIRED
            var mixedMenu by remember(feature) { mutableStateOf(false) }
            val bool = value as? Boolean
            fun change(checked: Boolean) = onEdit(feature, FeatureValue(checked, FeatureValueType.BOOLEAN))

            val first = index == 0
            val last = index == features.lastIndex
            val itemEnabled = enabled && supported

            val summaryContent: (@Composable () -> Unit)? = if (
                feature in state.edits || (supported && state.recovery(feature) == ConfigRecovery.SYSTEM_DEFAULT)
            ) {
                {
                    if (feature in state.edits) {
                        Text(
                            text = stringResource(R.string.ims_pending),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (supported && state.recovery(feature) == ConfigRecovery.SYSTEM_DEFAULT) {
                        Text(
                            text = stringResource(R.string.ims_system_default_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            enabled = enabled,
                            onClick = { change(false) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Text(stringResource(R.string.ims_restore_system_defaults))
                        }
                    }
                }
            } else null

            when {
                !supported -> {
                    GroupedSettingsItem(
                        title = stringResource(feature.showTitleRes),
                        summary = stringResource(feature.showDescriptionRes),
                        enabled = false,
                        first = first,
                        last = last,
                        summaryContent = summaryContent,
                        trailing = {
                            Text(
                                text = stringResource(R.string.ims_unknown),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }

                feature.valueType == FeatureValueType.STRING -> {
                    val strValue = value as? String
                    val summaryText = if (!strValue.isNullOrBlank()) strValue else stringResource(feature.showDescriptionRes)
                    GroupedSettingsItem(
                        title = stringResource(feature.showTitleRes),
                        summary = summaryText,
                        enabled = itemEnabled,
                        first = first,
                        last = last,
                        onClick = if (itemEnabled) { { onStringClick(feature) } } else null,
                        summaryContent = summaryContent,
                        trailing = {
                            TextButton(
                                onClick = { onStringClick(feature) },
                                enabled = itemEnabled,
                            ) {
                                Text(stringResource(R.string.ims_edit))
                            }
                        },
                    )
                }

                resetOnly -> {
                    GroupedSettingsItem(
                        title = stringResource(feature.showTitleRes),
                        summary = stringResource(feature.showDescriptionRes),
                        enabled = itemEnabled,
                        first = first,
                        last = last,
                        summaryContent = summaryContent,
                        trailing = {
                            TextButton(
                                enabled = itemEnabled,
                                onClick = {
                                    when {
                                        feature in state.edits && state.common[feature]?.data == false -> change(false)
                                        bool != true -> change(true)
                                        else -> onExplainReset()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        when {
                                            feature in state.edits && state.common[feature]?.data == false -> R.string.ims_undo
                                            bool == true -> R.string.ims_reset_to_restore
                                            else -> R.string.ims_enable
                                        }
                                    )
                                )
                            }
                        },
                    )
                }

                bool != null -> {
                    // 最新系统设置风格开关项：整行可点击切换，右侧放置 Switch
                    GroupedSettingsItem(
                        title = stringResource(feature.showTitleRes),
                        summary = stringResource(feature.showDescriptionRes),
                        enabled = itemEnabled,
                        first = first,
                        last = last,
                        modifier = Modifier.toggleable(
                            value = bool,
                            enabled = itemEnabled,
                            role = Role.Switch,
                            onValueChange = ::change,
                        ),
                        summaryContent = summaryContent,
                        trailing = {
                            Switch(
                                checked = bool,
                                enabled = itemEnabled,
                                onCheckedChange = null,
                            )
                        },
                    )
                }

                else -> {
                    // 各卡状态不同（混合状态），弹出菜单选择全部启用/禁用
                    Box {
                        GroupedSettingsItem(
                            title = stringResource(feature.showTitleRes),
                            summary = stringResource(feature.showDescriptionRes),
                            enabled = itemEnabled,
                            first = first,
                            last = last,
                            onClick = if (itemEnabled) { { mixedMenu = true } } else null,
                            summaryContent = summaryContent,
                            trailing = {
                                TextButton(
                                    onClick = { mixedMenu = true },
                                    enabled = itemEnabled,
                                ) {
                                    Text(stringResource(R.string.ims_mixed))
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = mixedMenu,
                            onDismissRequest = { mixedMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ims_enable)) },
                                onClick = {
                                    mixedMenu = false
                                    change(true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ims_disable)) },
                                onClick = {
                                    mixedMenu = false
                                    change(false)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
