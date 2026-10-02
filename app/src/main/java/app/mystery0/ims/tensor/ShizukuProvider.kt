package app.mystery0.ims.tensor

import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.util.Log
import app.mystery0.ims.tensor.privilege.OperationType
import app.mystery0.ims.tensor.privilege.PrivilegeRuntime
import app.mystery0.ims.tensor.privilege.SimReadResult
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.model.CaptivePortalSettings
import app.mystery0.ims.tensor.model.ImsCapabilityStatus
import app.mystery0.ims.tensor.model.PersistentVolteState
import app.mystery0.ims.tensor.model.SimSelection
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.model.TargetConfigSnapshot
import app.mystery0.ims.tensor.model.Feature
import app.mystery0.ims.tensor.model.FeatureValue
import app.mystery0.ims.tensor.privileged.ImsConfigurationReader
import app.mystery0.ims.tensor.privileged.BrokerInstrumentation
import app.mystery0.ims.tensor.privileged.CaptivePortalSettingsModifier
import app.mystery0.ims.tensor.privileged.ImsCapabilityReader
import app.mystery0.ims.tensor.privileged.ImsModifier
import app.mystery0.ims.tensor.privileged.ImsResetter
import app.mystery0.ims.tensor.privileged.PersistentVolteModifier
import app.mystery0.ims.tensor.privileged.SimReader
import app.mystery0.ims.tensor.privileged.toPrivilegedErrorMessage
import kotlinx.coroutines.CancellationException
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.ShizukuProvider

class ShizukuProvider : ShizukuProvider() {
    override fun onCreate(): Boolean {
        LSPass.setHiddenApiExemptions("")
        // 自动恢复由 Application 中的协调器按用户开关调度，此处仅初始化隐藏 API 访问。
        return super.onCreate()
    }

    companion object {
        private const val TAG = "ShizukuProvider"

        suspend fun readTargetConfig(context: Context, subId: Int): TargetConfigSnapshot {
            val args = Bundle().apply { putInt(TargetConfigProtocol.SUB_ID, subId) }
            return TargetConfigProtocol.snapshot(subId,
                startInstrumentation(context, ImsConfigurationReader::class.java, args, true))
        }

        suspend fun applyTargetConfig(
            context: Context,
            subId: Int,
            identity: String,
            targets: Map<Feature, FeatureValue>,
            canStart: () -> Boolean = { true },
        ): TargetConfigSnapshot {
            val args = Bundle().apply {
                putString(TargetConfigProtocol.ACTION, TargetConfigProtocol.APPLY)
                putInt(TargetConfigProtocol.SUB_ID, subId)
                putString(TargetConfigProtocol.IDENTITY, identity)
                putBundle(TargetConfigProtocol.VALUES, TargetConfigProtocol.encode(targets))
            }
            val result = startInstrumentation(context, ImsModifier::class.java, Bundle(args), true, canStart)
            return TargetConfigProtocol.snapshot(subId, result)
        }

        suspend fun persistentVolte(context: Context, subId: Int, action: String): PersistentVolteState {
            try {
                val args = Bundle().apply {
                    putInt(PersistentVolteModifier.SUB_ID, subId)
                    putString(PersistentVolteModifier.ACTION, action)
                }
                val result = startInstrumentation(context, PersistentVolteModifier::class.java, args, true)
                    ?: return PersistentVolteState(subId, error = "No result; refresh state before retrying")
                fun readBoolean(key: String): Boolean? =
                    if (result.containsKey(key)) result.getBoolean(key) else null
                return PersistentVolteState(
                    subId = subId,
                    optIn = readBoolean(PersistentVolteModifier.OPT_IN),
                    userEnabled = readBoolean(PersistentVolteModifier.USER_ENABLED),
                    imsRegistered = readBoolean(PersistentVolteModifier.IMS_REGISTERED),
                    canRestore = result.getBoolean(PersistentVolteModifier.CAN_RESTORE),
                    unsupported = result.getBoolean(PersistentVolteModifier.UNSUPPORTED),
                    error = result.getString(PersistentVolteModifier.ERROR)
                        ?: if (!result.getBoolean(PersistentVolteModifier.COMPLETED)) "Incomplete instrumentation result" else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "Persistent VoLTE request failed", t)
                return PersistentVolteState(subId, error = t.toPrivilegedErrorMessage())
            }
        }

        suspend fun readCaptivePortalSettings(
            context: Context,
        ): Pair<CaptivePortalSettings?, String?> {
            val args = Bundle().apply {
                putString(
                    CaptivePortalSettingsModifier.ACTION,
                    CaptivePortalSettingsModifier.ACTION_READ,
                )
            }
            val result = startInstrumentation(
                context,
                CaptivePortalSettingsModifier::class.java,
                args,
                true,
            ) ?: return null to "No instrumentation result"
            if (!result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) {
                return null to (result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
                    ?: "Incomplete captive portal result")
            }
            return CaptivePortalSettings(
                httpUrl = result.getString(CaptivePortalSettingsModifier.RESULT_HTTP_URL),
                httpsUrl = result.getString(CaptivePortalSettingsModifier.RESULT_HTTPS_URL),
            ) to null
        }

        suspend fun writeCaptivePortalSettings(
            context: Context,
            settings: CaptivePortalSettings,
        ): String? {
            val httpUrl = requireNotNull(settings.httpUrl)
            val httpsUrl = requireNotNull(settings.httpsUrl)
            val args = Bundle().apply {
                putString(
                    CaptivePortalSettingsModifier.ACTION,
                    CaptivePortalSettingsModifier.ACTION_WRITE,
                )
                putString(CaptivePortalSettingsModifier.HTTP_URL, httpUrl)
                putString(CaptivePortalSettingsModifier.HTTPS_URL, httpsUrl)
            }
            val result = startInstrumentation(
                context,
                CaptivePortalSettingsModifier::class.java,
                args,
                true,
            ) ?: return "No instrumentation result"
            return if (result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) {
                null
            } else {
                result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
                    ?: "Incomplete captive portal result"
            }
        }

        suspend fun resetCaptivePortalSettings(context: Context): String? {
            val args = Bundle().apply {
                putString(
                    CaptivePortalSettingsModifier.ACTION,
                    CaptivePortalSettingsModifier.ACTION_RESET,
                )
            }
            val result = startInstrumentation(
                context,
                CaptivePortalSettingsModifier::class.java,
                args,
                true,
            ) ?: return "No instrumentation result"
            return if (result.getBoolean(CaptivePortalSettingsModifier.RESULT_SUCCESS)) {
                null
            } else {
                result.getString(CaptivePortalSettingsModifier.RESULT_MESSAGE)
                    ?: "Incomplete captive portal result"
            }
        }

        suspend fun overrideImsConfig(
            context: Context,
            data: Bundle,
            canStart: () -> Boolean = { true },
        ): String? {
            val result = startInstrumentation(context, ImsModifier::class.java, Bundle(data), true, canStart)
                ?: return "No instrumentation result"
            return if (result.getBoolean(ImsModifier.BUNDLE_RESULT)) null
                else result.getString(ImsModifier.BUNDLE_RESULT_MSG) ?: "Incomplete configuration result"
        }

        suspend fun readImsCapabilities(context: Context, subId: Int): ImsCapabilityStatus? {
            val args = Bundle().apply {
                putInt(ImsCapabilityReader.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsCapabilityReader::class.java, args, true)
            if (result == null) {
                Log.w(TAG, "readImsCapabilities: failed with empty result")
                return null
            }
            if (result.getString(ImsCapabilityReader.BUNDLE_RESULT_MSG) != null) {
                Log.w(TAG, "readImsCapabilities: ${result.getString(ImsCapabilityReader.BUNDLE_RESULT_MSG)}")
                return null
            }
            val requiredKeys = listOf(
                ImsCapabilityReader.BUNDLE_IMS_REGISTERED, ImsCapabilityReader.BUNDLE_VOLTE,
                ImsCapabilityReader.BUNDLE_VOWIFI, ImsCapabilityReader.BUNDLE_VONR,
                ImsCapabilityReader.BUNDLE_VT, ImsCapabilityReader.BUNDLE_NR_NSA,
                ImsCapabilityReader.BUNDLE_NR_SA,
            )
            if (!requiredKeys.all(result::containsKey)) {
                Log.w(TAG, "readImsCapabilities: incomplete result")
                return null
            }
            return ImsCapabilityStatus(
                isRegistered = result.getBoolean(ImsCapabilityReader.BUNDLE_IMS_REGISTERED),
                isVolteAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VOLTE),
                isVoWifiAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VOWIFI),
                isVoNrAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VONR),
                isVtAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_VT),
                isNrNsaAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_NR_NSA),
                isNrSaAvailable = result.getBoolean(ImsCapabilityReader.BUNDLE_NR_SA),
            )
        }

        suspend fun resetIms(context: Context, subId: Int): String? {
            val args = Bundle().apply {
                putInt(ImsResetter.BUNDLE_SELECT_SIM_ID, subId)
            }
            val result = startInstrumentation(context, ImsResetter::class.java, args, true)
            if (result == null) return "No instrumentation result"
            if (!result.getBoolean(ImsResetter.BUNDLE_RESULT)) {
                return result.getString(ImsResetter.BUNDLE_RESULT_MSG) ?: "Incomplete IMS reset result"
            }
            return null
        }

        suspend fun readSimInfoList(context: Context): List<SimSelection> = readSimInfoResult(context).sims

        /** UI 必须检查 error；读取失败不能丢弃当前单卡选择与未应用草稿。 */
        suspend fun readSimInfoResult(context: Context): SimReadResult<SimSelection> {
            val result = startInstrumentation(context, SimReader::class.java, null, true)
            val error = result?.getString(BridgeProtocol.ERROR_CODE)?.let { code ->
                result.getString(BridgeProtocol.MESSAGE) ?: result.getString(ImsModifier.BUNDLE_RESULT_MSG) ?: code
            }
            val subList = try {
                result?.getParcelableArrayList(SimReader.BUNDLE_RESULT, SubscriptionInfo::class.java)
            } catch (failure: RuntimeException) {
                Log.w(TAG, "Invalid SIM result payload", failure)
                return SimReadResult(emptyList(), "Invalid SIM result payload")
            }
            return SimReadResult.fromPayload(subList?.map {
                SimSelection(it.subscriptionId, it.displayName.toString(), it.carrierName.toString(), it.simSlotIndex)
            }, error)
        }

        /** 保留业务 facade 签名，固定入口映射后由所选后端执行；fallback 归 Runtime 统一持锁。 */
        private suspend fun startInstrumentation(
            context: Context,
            cls: Class<*>,
            args: Bundle?,
            @Suppress("UNUSED_PARAMETER") receiveResult: Boolean,
            canStart: () -> Boolean = { true },
        ): Bundle? {
            val operation = when (cls) {
                SimReader::class.java -> OperationType.READ_SIMS
                ImsCapabilityReader::class.java -> OperationType.READ_CAPABILITIES
                ImsConfigurationReader::class.java -> OperationType.READ_CONFIG
                ImsModifier::class.java -> OperationType.APPLY_CONFIG
                BrokerInstrumentation::class.java -> OperationType.BROKER_CONFIG
                ImsResetter::class.java -> OperationType.RESET_IMS
                PersistentVolteModifier::class.java -> when (args?.getString(PersistentVolteModifier.ACTION)) {
                    PersistentVolteModifier.QUERY -> OperationType.READ_PERSISTENT_VOLTE
                    PersistentVolteModifier.ENABLE -> OperationType.SET_PERSISTENT_VOLTE
                    PersistentVolteModifier.RESTORE, PersistentVolteModifier.RESTORE_FOR_RESET -> OperationType.RESTORE_PERSISTENT_VOLTE
                    else -> error("Unknown persistent VoLTE action")
                }
                CaptivePortalSettingsModifier::class.java -> when (args?.getString(CaptivePortalSettingsModifier.ACTION)) {
                    CaptivePortalSettingsModifier.ACTION_READ -> OperationType.READ_CAPTIVE_PORTAL
                    CaptivePortalSettingsModifier.ACTION_WRITE -> OperationType.WRITE_CAPTIVE_PORTAL
                    CaptivePortalSettingsModifier.ACTION_RESET -> OperationType.RESET_CAPTIVE_PORTAL
                    else -> error("Unknown captive portal action")
                }
                else -> error("Instrumentation is not allowlisted")
            }
            return PrivilegeRuntime.execute(context, operation, args, canStart)
        }
    }
}
