package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState

enum class BackendPrimaryAction { CHOOSE_MODE, AUTHORIZE_OFFICIAL, OPEN_SETTINGS, REFRESH, WAIT, REVIEW_RECOVERY }

data class BackendUiActions(
    val primary: BackendPrimaryAction,
    val canChooseMode: Boolean,
    val canStartEmbedded: Boolean,
    val canRequestOfficialPermission: Boolean,
)

/** 界面只决定可见动作；运行时仍须在执行瞬间验证相同门禁，不排队切换。 */
fun backendUiActions(
    status: BackendStatus,
    actionInProgress: Boolean = false,
    businessBusy: Boolean = false,
    recoveryStartAllowed: Boolean = false,
    recoveryPermissionAllowed: Boolean = false,
): BackendUiActions {
    val recovery = status.connection == ConnectionState.RECOVERY_REQUIRED
    val locked = actionInProgress || businessBusy || status.connection in setOf(
        ConnectionState.CONNECTING, ConnectionState.BUSY, ConnectionState.SWITCHING,
        ConnectionState.RECOVERY_REQUIRED,
    )
    // 恢复期间只有运行时确认旧任务已经结束后，才允许明确的重新连接/授权。
    val safeRecovery = recovery && !actionInProgress && !businessBusy
    val permission = status.mode == BackendMode.OFFICIAL &&
        ((!locked && status.errorCode in setOf("PERMISSION_DENIED", "PERMISSION_REQUIRED")) ||
            (safeRecovery && recoveryPermissionAllowed))
    return BackendUiActions(
        primary = when {
            recovery -> BackendPrimaryAction.REVIEW_RECOVERY
            locked -> BackendPrimaryAction.WAIT
            status.mode == BackendMode.UNSET -> BackendPrimaryAction.CHOOSE_MODE
            permission -> BackendPrimaryAction.AUTHORIZE_OFFICIAL
            status.connection == ConnectionState.READY -> BackendPrimaryAction.REFRESH
            else -> BackendPrimaryAction.OPEN_SETTINGS
        },
        canChooseMode = !locked,
        canStartEmbedded = status.mode == BackendMode.EMBEDDED && !status.isReady &&
            (!locked || (safeRecovery && recoveryStartAllowed)),
        canRequestOfficialPermission = permission,
    )
}

fun canConfirmModeChoice(
    status: BackendStatus,
    target: BackendMode?,
    actionInProgress: Boolean = false,
    businessBusy: Boolean = false,
): Boolean = target != null && target != BackendMode.UNSET && target != status.mode &&
    backendUiActions(status, actionInProgress, businessBusy).canChooseMode

fun parseAdbPort(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }
    ?.toIntOrNull()?.takeIf { it in 1..65535 }

fun validPairingCode(text: String): Boolean = text.length == 6 && text.all { it in '0'..'9' }

fun canConfirmPersistentRecovery(
    status: BackendStatus,
    runtimeAllowsRecovery: Boolean,
    actionInProgress: Boolean = false,
    businessBusy: Boolean = false,
): Boolean = status.connection == ConnectionState.RECOVERY_REQUIRED && runtimeAllowsRecovery &&
    !actionInProgress && !businessBusy
