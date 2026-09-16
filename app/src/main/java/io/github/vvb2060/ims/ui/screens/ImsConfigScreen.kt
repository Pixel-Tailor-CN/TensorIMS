package io.github.vvb2060.ims.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection

private val callFeatures = listOf(
    Feature.VOLTE,
    Feature.VOWIFI,
    Feature.VOWIFI_ROAMING,
    Feature.VONR,
    Feature.VT,
    Feature.CROSS_SIM,
    Feature.UT,
)

private val networkFeatures = listOf(
    Feature.FIVE_G_NR,
    Feature.FIVE_G_THRESHOLDS,
)

private val displayFeatures = listOf(
    Feature.FIVE_G_PLUS_ICON,
    Feature.ENHANCED_4G_LTE,
    Feature.HIDE_LTE_PLUS_DATA_ICON,
    Feature.SHOW_4G_FOR_LTE,
)

private val advancedFeatures = listOf(
    Feature.CARRIER_NAME,
    Feature.IMS_USER_AGENT,
)

@Composable
fun ImsConfigScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    loadConfiguration: (Int) -> Map<Feature, FeatureValue>?,
    loadDefaults: () -> Map<Feature, FeatureValue>,
    onApplyConfiguration: (SimSelection, Map<Feature, FeatureValue>) -> Unit,
    onBack: () -> Unit,
) {
    val featureSwitches = remember(selectedSim?.subId) {
        mutableStateMapOf<Feature, FeatureValue>().apply {
            if (selectedSim != null) {
                putAll(loadConfiguration(selectedSim.subId) ?: loadDefaults())
            }
        }
    }
    var editingFeature by remember { mutableStateOf<Feature?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.ims_configuration))
                        selectedSim?.let {
                            Text(
                                text = it.showTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            enabled = selectedSim != null,
                            onClick = { showMenu = true },
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.load_saved_configuration)) },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val sim = selectedSim ?: return@DropdownMenuItem
                                    featureSwitches.clear()
                                    featureSwitches.putAll(loadConfiguration(sim.subId) ?: loadDefaults())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restore_default_draft)) },
                                leadingIcon = { Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    featureSwitches.clear()
                                    featureSwitches.putAll(loadDefaults())
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Button(
                    onClick = {
                        selectedSim?.let { onApplyConfiguration(it, featureSwitches.toMap()) }
                    },
                    enabled = selectedSim != null &&
                            shizukuStatus == ShizukuStatus.READY &&
                            !isOperationInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.apply_changes))
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (selectedSim == null) {
                Text(
                    text = stringResource(R.string.select_sim_first),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // 顶置心智说明横幅
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.ims_draft_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.ims_draft_banner_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            // 快速预设 Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {
                        featureSwitches[Feature.VOLTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VT] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI_ROAMING] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VONR] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_NR] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_THRESHOLDS] = FeatureValue(true, FeatureValueType.BOOLEAN)
                    },
                    label = { Text(stringResource(R.string.preset_recommended)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = {
                        featureSwitches[Feature.VOLTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI_ROAMING] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VONR] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VT] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.CROSS_SIM] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.UT] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_NR] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_THRESHOLDS] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_PLUS_ICON] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.ENHANCED_4G_LTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.HIDE_LTE_PLUS_DATA_ICON] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.SHOW_4G_FOR_LTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                    },
                    label = { Text(stringResource(R.string.preset_china_5g)) },
                )
                AssistChip(
                    onClick = {
                        featureSwitches[Feature.VOLTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VOWIFI_ROAMING] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VONR] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.VT] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.CROSS_SIM] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.UT] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_NR] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_THRESHOLDS] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.FIVE_G_PLUS_ICON] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.ENHANCED_4G_LTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.HIDE_LTE_PLUS_DATA_ICON] = FeatureValue(false, FeatureValueType.BOOLEAN)
                        featureSwitches[Feature.SHOW_4G_FOR_LTE] = FeatureValue(true, FeatureValueType.BOOLEAN)
                    },
                    label = { Text(stringResource(R.string.preset_china_lte)) },
                )
                AssistChip(
                    onClick = {
                        Feature.entries.filter { it.valueType == FeatureValueType.BOOLEAN }.forEach { feat ->
                            featureSwitches[feat] = FeatureValue(true, FeatureValueType.BOOLEAN)
                        }
                    },
                    label = { Text(stringResource(R.string.preset_all_enabled)) },
                )
            }

            FeatureSection(
                title = stringResource(R.string.feature_group_calling),
                features = callFeatures,
                values = featureSwitches,
                onBooleanChange = { feature, checked ->
                    featureSwitches[feature] = FeatureValue(checked, FeatureValueType.BOOLEAN)
                },
                onStringClick = { editingFeature = it },
            )
            FeatureSection(
                title = stringResource(R.string.feature_group_network),
                features = networkFeatures,
                values = featureSwitches,
                onBooleanChange = { feature, checked ->
                    featureSwitches[feature] = FeatureValue(checked, FeatureValueType.BOOLEAN)
                },
                onStringClick = { editingFeature = it },
            )
            FeatureSection(
                title = stringResource(R.string.feature_group_display),
                features = displayFeatures,
                values = featureSwitches,
                onBooleanChange = { feature, checked ->
                    featureSwitches[feature] = FeatureValue(checked, FeatureValueType.BOOLEAN)
                },
                onStringClick = { editingFeature = it },
            )
            if (selectedSim.subId != -1) {
                FeatureSection(
                    title = stringResource(R.string.feature_group_advanced_overrides),
                    features = advancedFeatures,
                    values = featureSwitches,
                    onBooleanChange = { feature, checked ->
                        featureSwitches[feature] = FeatureValue(checked, FeatureValueType.BOOLEAN)
                    },
                    onStringClick = { editingFeature = it },
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }

    editingFeature?.let { feature ->
        val current = featureSwitches[feature]?.data as? String ?: ""
        StringFeatureDialog(
            feature = feature,
            initialValue = current,
            onDismiss = { editingFeature = null },
            onSave = { value ->
                featureSwitches[feature] = FeatureValue(value, FeatureValueType.STRING)
                editingFeature = null
            },
        )
    }
}

@Composable
private fun FeatureSection(
    title: String,
    features: List<Feature>,
    values: Map<Feature, FeatureValue>,
    onBooleanChange: (Feature, Boolean) -> Unit,
    onStringClick: (Feature) -> Unit,
) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        features.forEachIndexed { index, feature ->
            val titleText = stringResource(feature.showTitleRes)
            val description = stringResource(feature.showDescriptionRes)
            when (feature.valueType) {
                FeatureValueType.BOOLEAN -> {
                    val checked = values[feature]?.data as? Boolean ?: feature.defaultValue as Boolean
                    ListItem(
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = { onBooleanChange(feature, it) },
                        ),
                        headlineContent = { Text(titleText) },
                        supportingContent = {
                            Text(
                                text = description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(checked = checked, onCheckedChange = null)
                        },
                    )
                }

                FeatureValueType.STRING -> {
                    val currentValue = values[feature]?.data as? String ?: ""
                    ListItem(
                        modifier = Modifier.clickable { onStringClick(feature) },
                        headlineContent = { Text(titleText) },
                        supportingContent = {
                            Text(
                                text = currentValue.ifBlank { description },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        },
                    )
                }
            }
            if (index != features.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun StringFeatureDialog(
    feature: Feature,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(feature, initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(feature.showTitleRes)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(feature.showTitleRes)) },
                supportingText = { Text(stringResource(feature.showDescriptionRes)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
