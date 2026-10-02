package app.mystery0.ims.tensor.privileged

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.IPrivilegeSession

/** 每个 Instrumentation 从本次参数读取显式会话，绝不读取默认后端单例。 */
open class SessionInstrumentation : Instrumentation() {
    lateinit var privilegeSession: IPrivilegeSession
        private set
    lateinit var operationId: String
        private set
    private var expectedIdentity: String? = null
    private var expectedIdentities: Bundle? = null
    private var selectedSubId = -1
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        requireNotNull(arguments) { "Missing privilege session" }
        require(arguments.getInt(BridgeProtocol.PROTOCOL) == BridgeProtocol.VERSION) { "VERSION_MISMATCH" }
        operationId = requireNotNull(arguments.getString(BridgeProtocol.OPERATION_ID))
        privilegeSession = IPrivilegeSession.Stub.asInterface(requireNotNull(arguments.getBinder(BridgeProtocol.SESSION)))
        expectedIdentity = arguments.getString(BridgeProtocol.TARGET_IDENTITY)
        expectedIdentities = arguments.getBundle(BridgeProtocol.TARGET_IDENTITIES)
        selectedSubId = when {
            arguments.containsKey("_target_sub_id") -> arguments.getInt("_target_sub_id", -1)
            arguments.containsKey("sub_id") -> arguments.getInt("sub_id", -1)
            else -> arguments.getInt("select_sim_id", -1)
        }
        // 移除传输元数据，旧版 CarrierConfig Bundle 转换也不能透传 Binder 或操作 ID。
        arguments.keySet().filter { it.startsWith(BridgeProtocol.PREFIX) }.forEach(arguments::remove)
    }
    internal fun requireExpectedIdentity(subId: Int): String =
        requireNotNull(if (selectedSubId == subId) expectedIdentity else expectedIdentities?.getString(subId.toString())) {
            "Missing expected SIM identity"
        }
    // 仅由成功建立本次 shell 权限委托的路径调用；统一边界捕获撤权后的 SecurityException。
    @SuppressLint("MissingPermission")
    internal fun verifyTargetIdentities() {
        expectedIdentity?.let { expected ->
            check(selectedSubId >= 0 && TargetConfigurationReader(context, selectedSubId).identity() == expected) { "SIM identity changed before operation" }
        }
        expectedIdentities?.let { identities ->
            val subscriptions = context.getSystemService(android.telephony.SubscriptionManager::class.java)
            val currentIds = subscriptions.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId }.toSet()
            check(currentIds == identities.keySet().map { it.toInt() }.toSet()) { "Active SIM set changed before operation" }
            identities.keySet().forEach { key ->
                check(TargetConfigurationReader(context, key.toInt()).identity() == identities.getString(key)) { "SIM identity changed before operation" }
            }
        }
    }
}
