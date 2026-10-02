package app.mystery0.ims.tensor.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.telephony.SubscriptionManager
import android.util.Log
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.CarrierBatchAdapter
import app.mystery0.ims.tensor.bridge.CarrierBatchRunner
import app.mystery0.ims.tensor.bridge.CarrierVerification
import app.mystery0.ims.tensor.bridge.CarrierWriteProgress
import app.mystery0.ims.tensor.model.TargetConfigMapper

/** 主路径与 Broker 共用逐卡写入/回读；旧历史 false 不生成新关闭值。 */
@Suppress("DEPRECATION")
// 仅由成功建立本次 shell 权限委托的路径调用；统一边界捕获撤权后的 SecurityException。
@SuppressLint("MissingPermission")
internal fun SessionInstrumentation.applyLegacyConfiguration(arguments: Bundle) {
    val result = Bundle()
    val resetRequested = arguments.getBoolean(ImsModifier.BUNDLE_RESET, false)
    val progress = CarrierWriteProgress()
    val observed = Bundle()
    var verified = emptySet<Int>()
    val failure = runWithShellPermissionDelegation("LegacyConfiguration") {
        val selected = arguments.getInt(ImsModifier.BUNDLE_SELECT_SIM_ID, -1)
        val reset = arguments.getBoolean(ImsModifier.BUNDLE_RESET, false)
        val expected = arguments.keySet().filterNot {
            it == ImsModifier.BUNDLE_SELECT_SIM_ID || it == ImsModifier.BUNDLE_RESET
        }.associateWith { requireNotNull(arguments.get(it)) }
        val targets = if (selected >= 0) listOf(selected) else
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }
        require(targets.isNotEmpty()) { "No active SIM available" }
        val identities = targets.associateWith { requireExpectedIdentity(it) }
        val readers = targets.associateWith { TargetConfigurationReader(context, it) }
        val values = if (reset) null else carrierValues(expected)
        val adapter = object : CarrierBatchAdapter {
            override fun identity(subId: Int) = readers.getValue(subId).identity()
            override fun alreadyMatches(subId: Int) = TargetConfigMapper.matches(readers.getValue(subId).raw(), expected)
            override fun write(subId: Int) = invokeCarrierOverride(readers.getValue(subId).manager, subId, values)
            override fun verify(subId: Int, resetting: Boolean): CarrierVerification {
                val reader = readers.getValue(subId)
                if (resetting) {
                    observed.putBundle(subId.toString(), TargetConfigurationReader.encode(reader.snapshot()))
                    // 合并后配置没有覆盖来源信息；不能从 framework 默认或任意一次回读推断覆盖已删除。
                    return CarrierVerification.UNPROVABLE
                }
                if (!TargetConfigMapper.matches(reader.raw(), expected)) return CarrierVerification.NOT_YET
                // 最终交付的快照本身必须核对目标，不能以较早一次读取代替成功证据。
                val verifiedSnapshot = reader.snapshot(expected)
                observed.putBundle(subId.toString(), TargetConfigurationReader.encode(verifiedSnapshot))
                return CarrierVerification.VERIFIED
            }
        }
        val outcome = CarrierBatchRunner(SystemClock::elapsedRealtime, Thread::sleep)
            .run(targets, identities, reset, adapter, progress)
        verified = outcome.verified
        outcome.failure?.let { throw it }
    }
    result.putBoolean(ImsModifier.BUNDLE_RESULT, failure == null)
    result.putIntArray(BridgeProtocol.VERIFIED_SUB_IDS, verified.toIntArray())
    result.putBundle(BridgeProtocol.OBSERVED_CONFIGS, observed)
    if (failure != null) {
        Log.e("LegacyConfiguration", "Carrier configuration result is not successful", failure)
        val message = if (resetRequested && progress.uncertainOnFailure) {
            "已尝试清除运营商配置覆盖，但系统没有提供可独立核验的删除完成证据，未记为重置成功。请先核对当前配置，自动恢复保持暂停。"
        } else failure.toPrivilegedErrorMessage()
        result.putString(ImsModifier.BUNDLE_RESULT_MSG, message)
        result.putString(BridgeProtocol.MESSAGE, message)
        if (progress.uncertainOnFailure) result.putString(BridgeProtocol.ERROR_CODE, "OPERATION_INDETERMINATE")
        else if (progress.retryAllowed) result.putBoolean(BridgeProtocol.BROKER_RETRY_ALLOWED, true)
    }
    finish(Activity.RESULT_OK, result)
}
