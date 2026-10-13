package app.mystery0.ims.tensor.privilege

import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionInfo
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.privileged.CaptivePortalSettingsModifier
import app.mystery0.ims.tensor.privileged.PersistentVolteModifier
import app.mystery0.ims.tensor.privileged.SimReader
import java.util.UUID

/** 丢失原目标时只读取当前状态；不重放写入、不恢复原值、不删除任何 SIM 备份。 */
internal object CorruptRecoveryReadback {
    suspend fun verify(context: Context, backend: PrivilegeBackend, epoch: Long): Boolean {
        suspend fun read(operation: OperationType, args: Bundle = Bundle()): Bundle? {
            val result = backend.execute(context, operation, args, UUID.randomUUID().toString(), epoch)
            if (result != null && !result.getBoolean(BridgeProtocol.CLEANUP_CONFIRMED)) {
                throw OperationUncertainException("Recovery permission cleanup is unconfirmed", cleanupUnconfirmed = true)
            }
            return result
        }
        val sims = read(OperationType.READ_SIMS)?.getParcelableArrayList(SimReader.BUNDLE_RESULT, SubscriptionInfo::class.java)
            ?: return false
        for (sim in sims) {
            val config = read(OperationType.READ_CONFIG, Bundle().apply {
                putInt(TargetConfigProtocol.SUB_ID, sim.subscriptionId)
            })
            val snapshot = TargetConfigProtocol.snapshot(sim.subscriptionId, config)
            if (snapshot.error != null || snapshot.identity.isBlank()) return false
            val volte = read(OperationType.READ_PERSISTENT_VOLTE, Bundle().apply {
                putInt(PersistentVolteModifier.SUB_ID, sim.subscriptionId)
                putString(PersistentVolteModifier.ACTION, PersistentVolteModifier.QUERY)
            }) ?: return false
            // 不支持持久化 VoLTE 的设备仍可使用其他功能；明确的不支持不等于清理未知。
            if (!RecoveryReadbackPolicy.persistentVolteConfirmed(
                    cleanupConfirmed = volte.getBoolean(BridgeProtocol.CLEANUP_CONFIRMED),
                    unsupported = volte.getBoolean(PersistentVolteModifier.UNSUPPORTED),
                    hasCriticalValues = volte.containsKey(PersistentVolteModifier.CAN_RESTORE) &&
                        volte.containsKey(PersistentVolteModifier.OPT_IN) && volte.containsKey(PersistentVolteModifier.USER_ENABLED),
                    error = volte.getString(PersistentVolteModifier.ERROR),
                    diagnosticWarning = volte.getString(BridgeProtocol.DIAGNOSTIC_WARNING),
                )) return false
        }
        val portal = read(OperationType.READ_CAPTIVE_PORTAL, Bundle().apply {
            putString(CaptivePortalSettingsModifier.ACTION, CaptivePortalSettingsModifier.ACTION_READ)
        }) ?: return false
        return portal.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS) &&
            portal.containsKey(CaptivePortalSettingsModifier.RESULT_HTTP_URL) &&
            portal.containsKey(CaptivePortalSettingsModifier.RESULT_HTTPS_URL)
    }
}
