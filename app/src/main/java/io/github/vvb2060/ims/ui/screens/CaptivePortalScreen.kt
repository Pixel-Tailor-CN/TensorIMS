package io.github.vvb2060.ims.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.CaptivePortalUrlError
import io.github.vvb2060.ims.viewmodel.CaptivePortalMode
import io.github.vvb2060.ims.viewmodel.CaptivePortalNotice
import io.github.vvb2060.ims.viewmodel.CaptivePortalUiState

@Composable
fun CaptivePortalScreen(
    state: CaptivePortalUiState,
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
            snackbarHostState.showSnackbar(noticeText)
            onNoticeShown()
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
                    enabled = !state.loading && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.captive_portal_experimental_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.captive_portal_experimental_description),
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

            if (state.mode == CaptivePortalMode.CUSTOM) {
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
                    CircularProgressIndicator()
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
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
