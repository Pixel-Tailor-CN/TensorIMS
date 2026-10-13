package app.mystery0.ims.tensor.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.privilege.BackendStatus
import app.mystery0.ims.tensor.privilege.OperationType
import app.mystery0.ims.tensor.privilege.RecoveryReason
import app.mystery0.ims.tensor.privilege.RecoveryStep

@Composable
fun BackendRecoveryGuidance(status: BackendStatus, compact: Boolean = false) {
    val recovery = status.recovery
    if (recovery == null) {
        Text(stringResource(R.string.backend_recovery_guidance))
        return
    }
    recovery.operation?.let { operation ->
        val label = when (operation) {
            OperationType.READ_SIMS -> R.string.recovery_operation_sims
            OperationType.READ_CAPABILITIES -> R.string.recovery_operation_ims_status
            OperationType.READ_CONFIG -> R.string.recovery_operation_read_config
            OperationType.APPLY_CONFIG, OperationType.BROKER_CONFIG -> R.string.recovery_operation_apply_config
            OperationType.RESET_IMS -> R.string.recovery_operation_reset_ims
            OperationType.READ_PERSISTENT_VOLTE -> R.string.recovery_operation_read_volte
            OperationType.SET_PERSISTENT_VOLTE -> R.string.recovery_operation_set_volte
            OperationType.RESTORE_PERSISTENT_VOLTE -> R.string.recovery_operation_restore_volte
            OperationType.READ_CAPTIVE_PORTAL -> R.string.recovery_operation_read_portal
            OperationType.WRITE_CAPTIVE_PORTAL, OperationType.RESET_CAPTIVE_PORTAL -> R.string.recovery_operation_write_portal
        }
        Text(stringResource(R.string.recovery_last_operation, stringResource(label)), style = MaterialTheme.typography.bodyMedium)
    }
    Text(stringResource(when (recovery.reason) {
        RecoveryReason.INTERRUPTED -> R.string.recovery_reason_interrupted
        RecoveryReason.TIMEOUT -> R.string.recovery_reason_timeout
        RecoveryReason.CANCELLED -> R.string.recovery_reason_cancelled
        RecoveryReason.CLEANUP_UNCONFIRMED -> R.string.recovery_reason_cleanup
        RecoveryReason.OUTCOME_UNKNOWN -> R.string.recovery_reason_outcome
        RecoveryReason.JOURNAL_IO -> R.string.recovery_reason_journal_io
        RecoveryReason.JOURNAL_CORRUPT -> R.string.recovery_reason_corrupt
    }), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(when (recovery.step) {
        RecoveryStep.WAIT_TASK -> R.string.recovery_step_wait
        RecoveryStep.RESTART_DEVICE -> R.string.recovery_step_restart
        RecoveryStep.REBOOT_UNVERIFIABLE -> R.string.recovery_step_reboot_unknown
        RecoveryStep.CHECKPOINT_FAILED -> R.string.recovery_step_checkpoint
        RecoveryStep.READBACK_REQUIRED -> R.string.recovery_step_refresh
        RecoveryStep.CONNECT_BACKEND -> when (recovery.connectionError) {
            "PERMISSION_REQUIRED", "PERMISSION_DENIED" -> R.string.recovery_step_permission
            "VERSION_MISMATCH", "AUTH_FAILED" -> R.string.backend_error_auth
            else -> if (status.mode == app.mystery0.ims.tensor.privilege.BackendMode.OFFICIAL)
                R.string.recovery_step_official else R.string.recovery_step_embedded
        }
        RecoveryStep.VERIFYING -> R.string.recovery_step_verifying
        RecoveryStep.READBACK_FAILED -> R.string.recovery_step_readback
        RecoveryStep.RESTORE_VOLTE -> R.string.recovery_step_volte
        RecoveryStep.CONFIRM_JOURNAL -> R.string.recovery_step_confirm_journal
    }), style = MaterialTheme.typography.bodySmall)
    if (!compact) Text(stringResource(R.string.recovery_keep_data), style = MaterialTheme.typography.bodySmall)
}
