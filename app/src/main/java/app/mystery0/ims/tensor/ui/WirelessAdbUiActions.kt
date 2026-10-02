package app.mystery0.ims.tensor.ui

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.embedded.WirelessAdbPhase
import app.mystery0.ims.tensor.embedded.WirelessAdbService
import app.mystery0.ims.tensor.embedded.WirelessAdbState

private enum class WirelessRequest { PAIR, CONNECT }
private enum class WirelessPermissionIssue { NOTIFICATIONS, LOCAL_NETWORK }

data class WirelessAdbUiActions(
    val pending: Boolean,
    val startPairing: () -> Unit,
    val startConnect: () -> Unit,
    val reopenSettings: () -> Unit,
)

/** 只有明确点击才申请权限；拒绝后提供设置入口，不在返回前台时自动再次申请。 */
@Composable
fun rememberWirelessAdbUiActions(
    state: WirelessAdbState,
    enabled: Boolean,
    onPair: () -> Boolean,
    onConnect: () -> Boolean,
): WirelessAdbUiActions {
    val context = LocalContext.current
    var pendingRequest by rememberSaveable { mutableStateOf<WirelessRequest?>(null) }
    var waitingForForegroundService by rememberSaveable { mutableStateOf(false) }
    var permissionIssue by rememberSaveable { mutableStateOf<WirelessPermissionIssue?>(null) }

    fun finishPermissionRequest() {
        val request = pendingRequest ?: return
        pendingRequest = null
        when {
            !WirelessAdbService.notificationAvailable(context) -> permissionIssue = WirelessPermissionIssue.NOTIFICATIONS
            Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) !=
                PackageManager.PERMISSION_GRANTED -> permissionIssue = WirelessPermissionIssue.LOCAL_NETWORK
            request == WirelessRequest.PAIR -> waitingForForegroundService = onPair()
            else -> onConnect()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // 重新读取实际权限与渠道状态，不能仅凭某个授权结果启动发现。
        finishPermissionRequest()
    }

    fun requestStart(request: WirelessRequest) {
        if (!enabled || state.active || pendingRequest != null || waitingForForegroundService) return
        pendingRequest = request
        val missing = wirelessAdbPermissionsToRequest(
            sdk = Build.VERSION.SDK_INT,
            notificationsGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            localNetworkGranted = Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED,
        )
        if (missing.isEmpty()) finishPermissionRequest() else permissionLauncher.launch(missing.toTypedArray())
    }

    LifecycleResumeEffect(state.phase, state.active, waitingForForegroundService) {
        // startForeground 已经完成后再离开应用；通知回复绝不通过这里拉起 Activity。
        if (wirelessAdbShouldOpenSettings(waitingForForegroundService, state.active, state.phase)) {
            waitingForForegroundService = false
            openWirelessAdbSettings(context)
        } else if (waitingForForegroundService && !state.active && !WirelessAdbService.state.value.active) {
            // 启动失败或进程重建后不能重放旧请求。
            waitingForForegroundService = false
        }
        onPauseOrDispose { }
    }

    permissionIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { permissionIssue = null },
            title = { Text(stringResource(if (issue == WirelessPermissionIssue.NOTIFICATIONS)
                R.string.wireless_ui_notifications_title else R.string.wireless_ui_network_title)) },
            text = { Text(stringResource(if (issue == WirelessPermissionIssue.NOTIFICATIONS)
                R.string.wireless_ui_notifications_required else R.string.wireless_ui_network_required)) },
            confirmButton = {
                TextButton(onClick = {
                    permissionIssue = null
                    val intent = if (issue == WirelessPermissionIssue.NOTIFICATIONS) {
                        val manager = context.getSystemService(NotificationManager::class.java)
                        val channelExists = manager.getNotificationChannel(WirelessAdbService.CHANNEL_ID) != null
                        Intent(if (manager.areNotificationsEnabled() && channelExists)
                            Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, WirelessAdbService.CHANNEL_ID)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    }
                    openSettings(context, intent)
                }) { Text(stringResource(R.string.wireless_ui_open_permission_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { permissionIssue = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    return WirelessAdbUiActions(
        pending = pendingRequest != null || waitingForForegroundService,
        startPairing = { requestStart(WirelessRequest.PAIR) },
        startConnect = { requestStart(WirelessRequest.CONNECT) },
        reopenSettings = { openWirelessAdbSettings(context) },
    )
}

/** 只消费本次前台发起的配对请求，回复通知后的状态不能再次拉起设置。 */
internal fun wirelessAdbShouldOpenSettings(waiting: Boolean, active: Boolean, phase: WirelessAdbPhase): Boolean =
    waiting && active && phase in setOf(WirelessAdbPhase.SEARCHING_PAIRING, WirelessAdbPhase.WAITING_CODE)

/** Android 17 的自动 NSD 发现需要本地网络权限；较旧版本不请求此权限或额外位置权限。 */
@SuppressLint("InlinedApi") // 此纯函数按传入 SDK 门禁加入内联权限字符串，便于覆盖各版本边界。
internal fun wirelessAdbPermissionsToRequest(sdk: Int, notificationsGranted: Boolean, localNetworkGranted: Boolean): List<String> =
    buildList {
        if (sdk >= 33 && !notificationsGranted) add(Manifest.permission.POST_NOTIFICATIONS)
        if (sdk >= 37 && !localNetworkGranted) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

private fun openWirelessAdbSettings(context: Context) =
    openSettings(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.wireless_ui_settings_unavailable, Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.wireless_ui_settings_unavailable, Toast.LENGTH_LONG).show()
    }
}
