package io.github.vvb2060.ims.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
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
                    IconButton(
                        enabled = selectedSim != null,
                        onClick = {
                            val sim = selectedSim ?: return@IconButton
                            featureSwitches.clear()
                            featureSwitches.putAll(loadConfiguration(sim.subId) ?: loadDefaults())
                        },
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.load_saved_configuration))
                    }
                    IconButton(
                        enabled = selectedSim != null,
                        onClick = {
                            featureSwitches.clear()
                            featureSwitches.putAll(loadDefaults())
                        },
                    ) {
                        Icon(
                            Icons.Rounded.SettingsBackupRestore,
                            contentDescription = stringResource(R.string.restore_default_draft),
                        )
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
            ImsTips()
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
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
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

@Composable
private fun ImsTips() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.tip),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val lines = stringArrayResource(R.array.tips)
        lines.forEach { text ->
            Text(
                text = text.removePrefix("!"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (text.startsWith("!")) FontWeight.Bold else null,
            )
        }
        val linkText = buildAnnotatedString {
            append(stringResource(R.string.tip_country_iso_prefix))
            withLink(
                LinkAnnotation.Url(
                    url = "https://github.com/ryfineZ/carrier-ims-for-pixel",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(stringResource(R.string.tip_country_iso_app_name))
            }
        }
        Text(
            text = linkText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
