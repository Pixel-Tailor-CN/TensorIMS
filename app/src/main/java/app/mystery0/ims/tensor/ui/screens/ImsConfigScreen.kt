package app.mystery0.ims.tensor.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import app.mystery0.ims.tensor.ui.components.pageContentInsets
import app.mystery0.ims.tensor.ui.components.pageContentPadding
import app.mystery0.ims.tensor.ui.components.ScrollEndSpacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.ConfigRecovery
import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.FeatureValue
import app.mystery0.ims.tensor.model.FeatureValueType
import app.mystery0.ims.tensor.model.ImsConfigPreset
import app.mystery0.ims.tensor.model.ImsEditorState
import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.model.imsConfigPreset
import app.mystery0.ims.tensor.ui.components.TargetFeatureSection

@Composable
fun ImsConfigScreen(
    selectedSim: SimSelection?,
    shizukuStatus: ShizukuStatus,
    isOperationInProgress: Boolean,
    state: ImsEditorState,
    onEdit: (Feature, FeatureValue) -> Unit,
    onPreset: (Map<Feature, FeatureValue>) -> Unit,
    onLoadHistory: () -> Unit,
    onRefresh: () -> Unit,
    onRetryRead: () -> Unit,
    onApply: () -> Unit,
    backendEpoch: Long,
    onReset: (SimSelection) -> Unit,
    onBack: () -> Unit,
) {
    var editingFeature by remember(selectedSim?.subId) { mutableStateOf<Feature?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmRefresh by remember { mutableStateOf(false) }
    var explainReset by remember(selectedSim?.subId, backendEpoch, shizukuStatus) { mutableStateOf(false) }
    val sameSelection = state.subId == selectedSim?.subId
    val ready = sameSelection && state.ready && shizukuStatus == ShizukuStatus.READY
    val canEdit = ready && !state.applying && !isOperationInProgress

    val canReset = (selectedSim?.subId ?: -1) >= 0 && shizukuStatus == ShizukuStatus.READY &&
        !state.applying && !state.loading && !isOperationInProgress

    Scaffold(
        contentWindowInsets = pageContentInsets,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Column {
                    Text(stringResource(R.string.ims_configuration))
                    selectedSim?.let { Text(it.showTitle, style = MaterialTheme.typography.labelSmall) }
                } },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                } },
                actions = { Box {
                    IconButton(onClick = { showMenu = true }, enabled = canEdit) {
                        Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.load_saved_configuration)) },
                            onClick = { showMenu = false; onLoadHistory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.ims_reload)) },
                            onClick = { showMenu = false; if (state.edits.isEmpty()) onRefresh() else confirmRefresh = true })
                    }
                } },
            )
        },
        bottomBar = { Column {
            Button(onClick = onApply, enabled = canEdit && state.edits.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(if (state.applying) R.string.ims_applying else R.string.apply_changes))
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        } },
    ) { innerPadding ->
        Column(Modifier.pageContentPadding(innerPadding).verticalScroll(rememberScrollState())) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.ims_target_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.ims_target_desc), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(when {
                        selectedSim == null -> R.string.select_sim_first
                        shizukuStatus != ShizukuStatus.READY -> R.string.backend_required
                        state.loading || !sameSelection -> R.string.ims_reading
                        state.applying -> R.string.ims_applying
                        !state.ready -> R.string.ims_read_failed
                        state.edits.isNotEmpty() -> R.string.ims_pending_changes
                        state.applied -> R.string.ims_verified
                        else -> R.string.ims_loaded
                    }), color = MaterialTheme.colorScheme.primary)
                }
            }
            if (sameSelection) {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                state.notice?.let { Text(stringResource(it), modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            if (!ready) {
                TextButton(onClick = onRetryRead, enabled = selectedSim != null &&
                    shizukuStatus == ShizukuStatus.READY && !state.loading && !isOperationInProgress) {
                    Text(stringResource(R.string.ims_reload))
                }
                ResetConfigurationEntry(canReset, selectedSim, { explainReset = true })
                ScrollEndSpacer(innerPadding)
                return@Column
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(ImsConfigPreset.RECOMMENDED to R.string.preset_recommended,
                    ImsConfigPreset.CHINA_5G to R.string.preset_china_5g,
                    ImsConfigPreset.CHINA_LTE to R.string.preset_china_lte,
                    ImsConfigPreset.ALL_ENABLED to R.string.preset_all_enabled)
                presets.forEach { (preset, label) -> AssistChip(onClick = { onPreset(imsConfigPreset(preset)) },
                    enabled = canEdit, label = { Text(stringResource(label)) }) }
            }

            val resetOnly = state.supported.filter { state.recovery(it) == ConfigRecovery.RESET_REQUIRED }.toSet()
            val groups = listOf(
                R.string.feature_group_calling to listOf(Feature.VOLTE, Feature.VOWIFI, Feature.VOWIFI_ROAMING,
                    Feature.VONR, Feature.VT, Feature.CROSS_SIM, Feature.UT),
                R.string.feature_group_network to listOf(Feature.FIVE_G_NR, Feature.FIVE_G_THRESHOLDS),
                R.string.feature_group_display to listOf(Feature.FIVE_G_PLUS_ICON, Feature.ENHANCED_4G_LTE,
                    Feature.HIDE_LTE_PLUS_DATA_ICON, Feature.SHOW_4G_FOR_LTE),
            )
            groups.forEach { (title, features) ->
                TargetFeatureSection(stringResource(title), features.filterNot { it in resetOnly }, state, canEdit,
                    onEdit, { editingFeature = it }, { explainReset = true })
            }
            val overrides = Feature.entries.filter { feature ->
                if (feature.valueType == FeatureValueType.STRING) selectedSim?.subId != -1 else feature in resetOnly
            }
            TargetFeatureSection(stringResource(R.string.ims_reset_only_group), overrides, state, canEdit,
                onEdit, { editingFeature = it }, { explainReset = true })
            if (overrides.isNotEmpty()) {
                Text(stringResource(R.string.ims_reset_required), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp))
            }
            ResetConfigurationEntry(canReset, selectedSim, { explainReset = true })
            Spacer(Modifier.size(16.dp))
            ScrollEndSpacer(innerPadding)
        }
    }

    if (confirmRefresh) AlertDialog(onDismissRequest = { confirmRefresh = false },
        title = { Text(stringResource(R.string.ims_reload)) },
        text = { Text(stringResource(R.string.ims_discard_draft)) },
        confirmButton = { TextButton(onClick = { confirmRefresh = false; onRefresh() }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { confirmRefresh = false }) { Text(stringResource(android.R.string.cancel)) } })

    if (explainReset) AlertDialog(onDismissRequest = { explainReset = false },
        title = { Text(stringResource(R.string.nav_reset_configuration)) },
        text = { Text(stringResource(R.string.reset_config_confirm, selectedSim?.showTitle.orEmpty())) },
        confirmButton = { TextButton(enabled = canReset, onClick = {
            explainReset = false
            selectedSim?.let(onReset)
        }) { Text(stringResource(R.string.nav_reset_configuration), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { explainReset = false }) { Text(stringResource(android.R.string.cancel)) } })

    editingFeature?.takeIf { canEdit }?.let { feature ->
        var text by remember(feature) { mutableStateOf(state.values[feature]?.data as? String ?: "") }
        AlertDialog(onDismissRequest = { editingFeature = null },
            title = { Text(stringResource(feature.showTitleRes)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text(stringResource(feature.showTitleRes)) },
                supportingText = { Text(stringResource(R.string.ims_string_reset_hint)) }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = {
                onEdit(feature, FeatureValue(text.trim(), FeatureValueType.STRING)); editingFeature = null
            }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editingFeature = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun ResetConfigurationEntry(enabled: Boolean, selectedSim: SimSelection?, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        TextButton(onClick = onClick, enabled = enabled) {
            Text(stringResource(R.string.nav_reset_configuration),
                color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
        if ((selectedSim?.subId ?: -1) < 0) Text(stringResource(R.string.nav_reset_single_sim),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
