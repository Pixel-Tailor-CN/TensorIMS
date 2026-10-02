package app.mystery0.ims.tensor.ui

import app.mystery0.ims.tensor.model.ShizukuStatus
import app.mystery0.ims.tensor.model.asLegacyUiStatus
import app.mystery0.ims.tensor.privilege.BackendMode
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class BackendUiLogicTest {
    private fun status(mode: BackendMode, state: ConnectionState, error: String? = null) =
        BackendStatus(mode, state, 0L, errorCode = error)

    @Test fun `未选择时只提供选择模式而不启动特权`() {
        val actions = backendUiActions(status(BackendMode.UNSET, ConnectionState.DISCONNECTED))
        assertEquals(BackendPrimaryAction.CHOOSE_MODE, actions.primary)
        assertTrue(actions.canChooseMode)
        assertFalse(actions.canStartEmbedded)
        assertFalse(actions.canRequestOfficialPermission)
    }

    @Test fun `官方拒绝授权后只允许用户重试授权且不会启动内置`() {
        val actions = backendUiActions(status(BackendMode.OFFICIAL, ConnectionState.BLOCKED, "PERMISSION_DENIED"))
        assertEquals(BackendPrimaryAction.AUTHORIZE_OFFICIAL, actions.primary)
        assertTrue(actions.canRequestOfficialPermission)
        assertFalse(actions.canStartEmbedded)
    }

    @Test fun `本地动作未结束时重复点击不入队`() {
        val actions = backendUiActions(status(BackendMode.EMBEDDED, ConnectionState.DISCONNECTED), actionInProgress = true)
        assertFalse(actions.canChooseMode)
        assertFalse(actions.canStartEmbedded)
        assertEquals(BackendPrimaryAction.WAIT, actions.primary)
    }

    @Test fun `写入连接切换和恢复期间不允许选择或启动`() {
        listOf(ConnectionState.BUSY, ConnectionState.CONNECTING, ConnectionState.SWITCHING, ConnectionState.RECOVERY_REQUIRED).forEach { phase ->
            val actions = backendUiActions(status(BackendMode.EMBEDDED, phase))
            assertFalse("$phase", actions.canChooseMode)
            assertFalse("$phase", actions.canStartEmbedded)
            assertFalse("$phase", actions.canRequestOfficialPermission)
        }
    }

    @Test fun `业务事务尚未结束时拒绝切换`() {
        assertFalse(backendUiActions(status(BackendMode.OFFICIAL, ConnectionState.READY), businessBusy = true).canChooseMode)
    }

    @Test fun `需要恢复时显示核对而非重新启动`() {
        assertEquals(BackendPrimaryAction.REVIEW_RECOVERY,
            backendUiActions(status(BackendMode.EMBEDDED, ConnectionState.RECOVERY_REQUIRED)).primary)
    }

    @Test fun `内置断开只给手动启动入口且已就绪不重复启动`() {
        assertTrue(backendUiActions(status(BackendMode.EMBEDDED, ConnectionState.DISCONNECTED)).canStartEmbedded)
        assertFalse(backendUiActions(status(BackendMode.EMBEDDED, ConnectionState.READY)).canStartEmbedded)
    }

    @Test fun `模式选择取消保持未选择且确认不能携带过期状态`() {
        assertFalse(canConfirmModeChoice(status(BackendMode.UNSET, ConnectionState.DISCONNECTED), null))
        assertFalse(canConfirmModeChoice(status(BackendMode.EMBEDDED, ConnectionState.BUSY), BackendMode.OFFICIAL))
        assertFalse(canConfirmModeChoice(status(BackendMode.OFFICIAL, ConnectionState.READY), BackendMode.OFFICIAL))
        assertTrue(canConfirmModeChoice(status(BackendMode.UNSET, ConnectionState.DISCONNECTED), BackendMode.EMBEDDED))
    }

    @Test fun `端口和配对码分别校验且保留前导零`() {
        assertEquals(37123, parseAdbPort("37123"))
        assertNull(parseAdbPort("0")); assertNull(parseAdbPort("65536")); assertNull(parseAdbPort("abc"))
        assertTrue(validPairingCode("012345"))
        assertFalse(validPairingCode("12345")); assertFalse(validPairingCode("１２３４５６"))
    }

    @Test fun `恢复按钮只在运行时确认可恢复且空闲时允许`() {
        val recovery = status(BackendMode.EMBEDDED, ConnectionState.RECOVERY_REQUIRED)
        assertTrue(canConfirmPersistentRecovery(recovery, true))
        assertFalse(canConfirmPersistentRecovery(recovery, false))
        assertFalse(canConfirmPersistentRecovery(recovery, true, actionInProgress = true))
        assertFalse(canConfirmPersistentRecovery(recovery, true, businessBusy = true))
        assertFalse(canConfirmPersistentRecovery(status(BackendMode.EMBEDDED, ConnectionState.READY), true))
    }

    @Test fun `已认证的忙碌后端保留页面草稿但启动中的未知身份不视为就绪`() {
        val busy = status(BackendMode.EMBEDDED, ConnectionState.BUSY)
        assertEquals(ShizukuStatus.READY, busy.copy(runtimeUid = 2000).asLegacyUiStatus())
        assertEquals(ShizukuStatus.NOT_RUNNING, busy.asLegacyUiStatus())
        assertEquals(ShizukuStatus.NOT_RUNNING, busy.copy(connection = ConnectionState.RECOVERY_REQUIRED).asLegacyUiStatus())
        assertFalse(busy.isReady)
    }

    @Test fun `恢复期只有运行时确认安全时可以重连且不能切换模式`() {
        val embedded = status(BackendMode.EMBEDDED, ConnectionState.RECOVERY_REQUIRED)
        val start = backendUiActions(embedded, recoveryStartAllowed = true)
        assertTrue(start.canStartEmbedded)
        assertFalse(start.canChooseMode)
        assertFalse(backendUiActions(embedded, recoveryStartAllowed = true, actionInProgress = true).canStartEmbedded)
        assertFalse(backendUiActions(embedded.copy(connection = ConnectionState.BUSY), recoveryStartAllowed = true).canStartEmbedded)
        val official = status(BackendMode.OFFICIAL, ConnectionState.RECOVERY_REQUIRED)
        assertTrue(backendUiActions(official, recoveryPermissionAllowed = true).canRequestOfficialPermission)
        assertFalse(backendUiActions(official, recoveryStartAllowed = true).canStartEmbedded)
        assertFalse(backendUiActions(embedded, recoveryPermissionAllowed = true).canRequestOfficialPermission)
    }
}
