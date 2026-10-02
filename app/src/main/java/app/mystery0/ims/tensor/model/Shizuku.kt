package app.mystery0.ims.tensor.model

import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState

/** 旧业务页面的兼容类型，实际后端状态与授权事实来自 PrivilegeRuntime。 */
enum class ShizukuStatus {
    CHECKING,
    NOT_RUNNING,
    NO_PERMISSION,
    READY,
    NEED_UPDATE,
}

/** BUSY 保留已认证的页面快照；所有提交另检查运行时和业务忙碌门禁。 */
fun BackendStatus.asLegacyUiStatus(): ShizukuStatus = when {
    connection == ConnectionState.READY || (connection == ConnectionState.BUSY && runtimeUid != null) -> ShizukuStatus.READY
    errorCode in setOf("PERMISSION_REQUIRED", "PERMISSION_DENIED") -> ShizukuStatus.NO_PERMISSION
    errorCode == "VERSION_MISMATCH" -> ShizukuStatus.NEED_UPDATE
    connection in setOf(ConnectionState.CONNECTING, ConnectionState.SWITCHING) -> ShizukuStatus.CHECKING
    else -> ShizukuStatus.NOT_RUNNING
}
