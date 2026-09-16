package io.github.vvb2060.ims.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.PersistentVolteState

/** 独立即时操作区域，不与“本次应用”的配置草稿混用。 */
@Composable
fun PersistentVolteCard(
    state: PersistentVolteState?,
    singleSimSelected: Boolean,
    shizukuReady: Boolean,
    busy: Boolean,
    onEnable: () -> Unit,
    onRestore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val canOperate = singleSimSelected && shizukuReady && !busy

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 卡片头部：标题与刷新按钮分列两侧，互不挤压
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.persistent_volte_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = canOperate,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh))
                }
            }

            Text(
                text = stringResource(R.string.persistent_volte_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                !singleSimSelected -> {
                    Text(
                        text = stringResource(R.string.persistent_volte_select_sim),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !shizukuReady -> {
                    Text(
                        text = stringResource(R.string.persistent_volte_shizuku_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    // 指标项使用整洁的 Key-Value 胶囊行，确保中英文长文本均有充足空间，杜绝截断
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StatusMetricRow(
                                label = stringResource(R.string.persistent_volte_opt_in),
                                value = state?.optIn,
                            )
                            StatusMetricRow(
                                label = stringResource(R.string.persistent_volte_user_setting),
                                value = state?.userEnabled,
                            )
                            StatusMetricRow(
                                label = stringResource(R.string.persistent_volte_registration),
                                value = state?.imsRegistered,
                            )
                        }
                    }

                    if (state?.unsupported == true) {
                        Text(
                            text = stringResource(R.string.persistent_volte_unsupported),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state?.error?.let {
                        Text(
                            text = stringResource(R.string.persistent_volte_error, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // 核心操作按钮行：仅保留主要操作与恢复，不再受刷新按钮挤压
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onEnable,
                    enabled = canOperate && state != null && !state.unsupported &&
                            state.optIn != null && state.userEnabled != null,
                ) {
                    Text(stringResource(R.string.persistent_volte_enable))
                }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = canOperate && state?.canRestore == true,
                ) {
                    Text(stringResource(R.string.persistent_volte_restore))
                }
            }

            // 说明与限制
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.persistent_volte_usage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.persistent_volte_restore_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = stringResource(R.string.persistent_volte_limits),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusMetricRow(
    label: String,
    value: Boolean?,
) {
    val (statusText, badgeColor, textColor) = when (value) {
        true -> Triple(
            stringResource(R.string.persistent_volte_on),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        false -> Triple(
            stringResource(R.string.persistent_volte_off),
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        null -> Triple(
            stringResource(R.string.persistent_volte_unknown),
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.outline,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor,
            contentColor = textColor,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
