package io.github.vvb2060.ims.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.CaptivePortalUrlError
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.viewmodel.CaptivePortalMode
import io.github.vvb2060.ims.viewmodel.CaptivePortalNotice
import io.github.vvb2060.ims.viewmodel.CaptivePortalUiState

private data class CaptivePortalPreset(
    val nameRes: Int,
    val httpUrl: String,
    val httpsUrl: String,
)

private val captivePortalPresets = listOf(
    CaptivePortalPreset(
        R.string.preset_google,
        "http://connectivitycheck.gstatic.com/generate_204",
        "https://connectivitycheck.gstatic.com/generate_204",
    ),
    CaptivePortalPreset(
        R.string.preset_v2ex,
        "http://captive.v2ex.co/generate_204",
        "https://captive.v2ex.co/generate_204",
    ),
    CaptivePortalPreset(
        R.string.preset_miui,
        "http://connect.rom.miui.com/generate_204",
        "https://connect.rom.miui.com/generate_204",
    ),
    CaptivePortalPreset(
        R.string.preset_vivo,
        "http://wifi.vivo.com.cn/generate_204",
        "https://wifi.vivo.com.cn/generate_204",
    ),
    CaptivePortalPreset(
        R.string.preset_huawei,
        "http://connectivitycheck.platform.hicloud.com/generate_204",
        "https://connectivitycheck.platform.hicloud.com/generate_204",
    ),
)

@Composable
fun CaptivePortalScreen(
    state: CaptivePortalUiState,
    shizukuStatus: ShizukuStatus,
    onRefresh: () -> Unit,
    onModeChange: (CaptivePortalMode) -> Unit,
    onHttpUrlChange: (String) -> Unit,
    onHttpsUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onNoticeShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = when (state.notice) {
        CaptivePortalNotice.SAVED -> stringResource(R.string.captive_portal_saved_notice)
        CaptivePortalNotice.RESET -> stringResource(R.string.captive_portal_reset_notice)
        CaptivePortalNotice.NONE -> ""
    }

    LaunchedEffect(Unit) {
        onRefresh()
    }
    LaunchedEffect(state.notice, noticeText) {
        if (state.notice != CaptivePortalNotice.NONE) {
            // 先消费一次性事件，避免用户在 Snackbar 展示期间离开再返回后重复提示。
            onNoticeShown()
            snackbarHostState.showSnackbar(noticeText)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.captive_portal)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.loading && !state.saving,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Button(
                    onClick = onSave,
                    enabled = !state.loading && !state.saving && shizukuStatus == ShizukuStatus.READY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        if (state.mode == CaptivePortalMode.SYSTEM_DEFAULT) {
                            stringResource(R.string.restore_system_default)
                        } else {
                            stringResource(R.string.save)
                        },
                    )
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 警示容器
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.captive_portal_experimental_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.captive_portal_experimental_description),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.captive_portal_description),
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ModeRow(
                title = stringResource(R.string.captive_portal_system_default),
                summary = stringResource(R.string.captive_portal_system_default_summary),
                selected = state.mode == CaptivePortalMode.SYSTEM_DEFAULT,
                onClick = { onModeChange(CaptivePortalMode.SYSTEM_DEFAULT) },
            )
            ModeRow(
                title = stringResource(R.string.captive_portal_custom),
                summary = stringResource(R.string.captive_portal_custom_summary),
                selected = state.mode == CaptivePortalMode.CUSTOM,
                onClick = { onModeChange(CaptivePortalMode.CUSTOM) },
            )

            AnimatedVisibility(
                visible = state.mode == CaptivePortalMode.CUSTOM,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = stringResource(R.string.captive_portal_presets),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        captivePortalPresets.forEach { preset ->
                            val isSelected = state.httpUrl == preset.httpUrl && state.httpsUrl == preset.httpsUrl
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onHttpUrlChange(preset.httpUrl)
                                    onHttpsUrlChange(preset.httpsUrl)
                                },
                                label = { Text(stringResource(preset.nameRes)) },
                            )
                        }
                    }
                    CaptivePortalTextField(
                        label = stringResource(R.string.captive_portal_http_url),
                        value = state.httpUrl,
                        onValueChange = onHttpUrlChange,
                        error = captivePortalErrorText(state.httpError, "http://"),
                    )
                    CaptivePortalTextField(
                        label = stringResource(R.string.captive_portal_https_url),
                        value = state.httpsUrl,
                        onValueChange = onHttpsUrlChange,
                        error = captivePortalErrorText(state.httpsError, "https://"),
                    )
                }
            }

            state.operationError?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.loading),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.captive_portal_reconnect_note),
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptivePortalTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
) {
    val clipboardManager = LocalClipboardManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            } else {
                IconButton(onClick = {
                    clipboardManager.getText()?.text?.let { onValueChange(it) }
                }) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(R.string.paste),
                    )
                }
            }
        },
    )
}

@Composable
private fun captivePortalErrorText(
    error: CaptivePortalUrlError?,
    expectedScheme: String,
): String? = when (error) {
    CaptivePortalUrlError.REQUIRED -> stringResource(R.string.captive_portal_error_required)
    CaptivePortalUrlError.INVALID_URL -> stringResource(R.string.captive_portal_error_invalid_url)
    CaptivePortalUrlError.WRONG_SCHEME -> stringResource(
        R.string.captive_portal_error_wrong_scheme,
        expectedScheme,
    )
    null -> null
}
