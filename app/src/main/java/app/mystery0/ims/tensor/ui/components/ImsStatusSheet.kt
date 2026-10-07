package app.mystery0.ims.tensor.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.model.ImsCapabilityStatus
import app.mystery0.ims.tensor.model.SimSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** 状态面板独立选择查看对象，不改变首页的批量配置目标。 */
@Composable
fun ImsStatusSheet(
    sims: List<SimSelection>,
    selectedSim: SimSelection?,
    ready: Boolean,
    busy: Boolean,
    onLoad: suspend (Int) -> ImsCapabilityStatus?,
    onRestart: (SimSelection, (Boolean) -> Unit) -> Unit,
    onOpenBackend: () -> Unit,
    onDismiss: () -> Unit,
) {
    var inspectedId by remember { mutableStateOf(selectedSim?.subId?.takeIf { it >= 0 } ?: sims.firstOrNull()?.subId) }
    val target = sims.firstOrNull { it.subId == inspectedId }
    LaunchedEffect(sims) {
        if (target == null) inspectedId = sims.firstOrNull()?.subId
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_ims_status), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
            }
            if (selectedSim?.subId == -1 && sims.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sims.forEach { sim ->
                        FilterChip(selected = inspectedId == sim.subId, onClick = { inspectedId = sim.subId },
                            label = { Text(sim.showTitle) }, enabled = !busy)
                    }
                }
            }
            target?.let { Text(it.showTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // 更换卡或失去后端时销毁旧读取、确认和结果，不能把旧卡响应显示到新卡。
            key(target?.subId, ready) {
                ImsStatusContent(target, ready, busy, onLoad, onRestart, onOpenBackend)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImsStatusContent(
    target: SimSelection?,
    ready: Boolean,
    busy: Boolean,
    onLoad: suspend (Int) -> ImsCapabilityStatus?,
    onRestart: (SimSelection, (Boolean) -> Unit) -> Unit,
    onOpenBackend: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<ImsCapabilityStatus?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }
    var restartResult by remember { mutableStateOf<Boolean?>(null) }
    val latestBusy by rememberUpdatedState(busy)
    var attached by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { attached = false } }

    LaunchedEffect(refresh) {
        if (!ready || target == null) { loading = false; return@LaunchedEffect }
        loading = true
        status = null
        // 读取也遵循现有业务串行边界；忙碌结束只放行本次请求，不造成刷新循环。
        snapshotFlow { latestBusy }.first { !it }
        status = try { onLoad(target.subId) } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { null }
        loading = false
    }

    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !ready -> {
                Text(stringResource(R.string.backend_required))
                TextButton(onClick = onOpenBackend) { Text(stringResource(R.string.backend_settings)) }
            }
            target == null -> Text(stringResource(R.string.backend_no_sim_available))
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.loading))
            }
            status == null -> Text(stringResource(R.string.load_system_config_error), color = MaterialTheme.colorScheme.error)
            else -> status?.let { value ->
                StatusRow("IMS", stringResource(if (value.isRegistered) R.string.ims_status_registered else R.string.ims_status_not_registered), value.isRegistered)
                listOf("VoLTE" to value.isVolteAvailable, "VoWiFi" to value.isVoWifiAvailable,
                    "VoNR" to value.isVoNrAvailable, stringResource(R.string.vt) to value.isVtAvailable,
                    "NR NSA" to value.isNrNsaAvailable, "NR SA" to value.isNrSaAvailable).forEach { (name, available) ->
                    StatusRow(name, stringResource(if (available) R.string.status_available else R.string.status_unavailable), available)
                }
                Text(stringResource(R.string.nav_snapshot_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        restartResult?.let { success ->
            Text(stringResource(if (success) R.string.nav_restart_done else R.string.nav_restart_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = if (success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
        if (ready && target != null) {
            val canAct = !busy && !loading && !restarting
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { refresh++ }, enabled = canAct) { Text(stringResource(R.string.refresh)) }
                OutlinedButton(onClick = { confirmRestart = true }, enabled = canAct) {
                    Text(stringResource(if (restarting) R.string.nav_restarting else R.string.restart_ims))
                }
            }
            if (confirmRestart) AlertDialog(
                onDismissRequest = { confirmRestart = false },
                title = { Text(stringResource(R.string.restart_ims)) },
                text = { Text(stringResource(R.string.restart_ims_confirm, target.showTitle)) },
                confirmButton = { TextButton(enabled = canAct, onClick = {
                    confirmRestart = false
                    restarting = true
                    restartResult = null
                    onRestart(target) { success ->
                        if (attached) {
                            restarting = false
                            restartResult = success
                            if (success) refresh++
                        }
                    }
                }) { Text(stringResource(R.string.restart_ims)) } },
                dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(android.R.string.cancel)) } },
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, available: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = MaterialTheme.shapes.small,
            color = if (available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (available) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(value, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
