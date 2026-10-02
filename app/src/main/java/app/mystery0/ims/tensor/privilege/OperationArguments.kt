package app.mystery0.ims.tensor.privilege

import android.os.Bundle
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.OperationActionPolicy
import app.mystery0.ims.tensor.bridge.IdentityRequirementPolicy
import app.mystery0.ims.tensor.model.TargetConfigProtocol
import app.mystery0.ims.tensor.model.validateCaptivePortalUrls
import app.mystery0.ims.tensor.privileged.CaptivePortalSettingsModifier as Captive
import app.mystery0.ims.tensor.privileged.PersistentVolteModifier as Volte
import app.mystery0.ims.tensor.privileged.ImsModifier

/** 按操作重建参数，阻止把写入 action 塞入只读操作或把桥接元数据写入 CarrierConfig。 */
object OperationArguments {
    fun preflight(operation: OperationType, arguments: Bundle): Bundle = validateInternal(operation, arguments, false)
    fun validate(operation: OperationType, arguments: Bundle): Bundle = validateInternal(operation, arguments, true)

    @Suppress("DEPRECATION")
    private fun validateInternal(operation: OperationType, raw: Bundle, requireCapturedIdentity: Boolean): Bundle {
        val input = Bundle(raw)
        require(!input.containsKey(BridgeProtocol.TARGET_IDENTITY) || input.get(BridgeProtocol.TARGET_IDENTITY) is String) { "INVALID_ARGUMENTS: identity type" }
        require(!input.containsKey(BridgeProtocol.TARGET_IDENTITIES) || input.get(BridgeProtocol.TARGET_IDENTITIES) is Bundle) { "INVALID_ARGUMENTS: identities type" }
        val identity = input.getString(BridgeProtocol.TARGET_IDENTITY)
        val identities = input.getBundle(BridgeProtocol.TARGET_IDENTITIES)
        require(identity == null || identity.matches(Regex("[a-fA-F0-9]{64}"))) { "INVALID_ARGUMENTS: identity" }
        require(identities == null || identities.size() in 1..8 && identities.keySet().all { key ->
            key.toIntOrNull()?.let { it >= 0 } == true && identities.getString(key).orEmpty().matches(Regex("[a-fA-F0-9]{64}"))
        }) { "INVALID_ARGUMENTS: identities" }
        input.remove(BridgeProtocol.TARGET_IDENTITY); input.remove(BridgeProtocol.TARGET_IDENTITIES)
        require(input.size() <= 128) { "INVALID_ARGUMENTS: too many keys" }
        require(input.keySet().none { it.startsWith("tensorims.bridge.") }) { "INVALID_ARGUMENTS: reserved key" }
        val result = Bundle(input)
        when (operation) {
            OperationType.READ_SIMS -> require(input.isEmpty) { "INVALID_ARGUMENTS" }
            OperationType.READ_CAPABILITIES, OperationType.RESET_IMS -> {
                keys(input, setOf("select_sim_id")); target(input, "select_sim_id", operation == OperationType.RESET_IMS)
            }
            OperationType.READ_CONFIG -> { keys(input, setOf(TargetConfigProtocol.SUB_ID)); target(input, TargetConfigProtocol.SUB_ID, false) }
            OperationType.READ_PERSISTENT_VOLTE, OperationType.SET_PERSISTENT_VOLTE, OperationType.RESTORE_PERSISTENT_VOLTE -> {
                keys(input, setOf(Volte.SUB_ID, Volte.ACTION))
                require(OperationActionPolicy.valid(operation.name, input.getString(Volte.ACTION))) { "INVALID_ARGUMENTS: action mismatch" }
                target(input, Volte.SUB_ID, input.getString(Volte.ACTION) == Volte.RESTORE_FOR_RESET)
            }
            OperationType.READ_CAPTIVE_PORTAL, OperationType.WRITE_CAPTIVE_PORTAL, OperationType.RESET_CAPTIVE_PORTAL -> {
                val write = operation == OperationType.WRITE_CAPTIVE_PORTAL
                keys(input, if (write) setOf(Captive.ACTION, Captive.HTTP_URL, Captive.HTTPS_URL) else setOf(Captive.ACTION))
                require(OperationActionPolicy.valid(operation.name, input.getString(Captive.ACTION))) { "INVALID_ARGUMENTS: action mismatch" }
                if (write) {
                    val http = requireNotNull(input.getString(Captive.HTTP_URL)); val https = requireNotNull(input.getString(Captive.HTTPS_URL))
                    require(http.length <= 2048 && https.length <= 2048) { "INVALID_ARGUMENTS: URL length" }
                    require(validateCaptivePortalUrls(http, https).isValid) { "INVALID_ARGUMENTS: URL" }
                }
            }
            OperationType.APPLY_CONFIG, OperationType.BROKER_CONFIG -> {
                if (input.containsKey(TargetConfigProtocol.ACTION)) {
                    keys(input, setOf(TargetConfigProtocol.ACTION, TargetConfigProtocol.SUB_ID, TargetConfigProtocol.IDENTITY, TargetConfigProtocol.VALUES))
                    require(input.getString(TargetConfigProtocol.ACTION) == TargetConfigProtocol.APPLY) { "INVALID_ARGUMENTS: action mismatch" }
                    target(input, TargetConfigProtocol.SUB_ID, false)
                    require(input.getString(TargetConfigProtocol.IDENTITY).orEmpty().matches(Regex("[a-fA-F0-9]{64}"))) { "INVALID_ARGUMENTS: SIM identity" }
                    val values = requireNotNull(input.getBundle(TargetConfigProtocol.VALUES))
                    val decoded = TargetConfigProtocol.decode(values)
                    require(decoded.isNotEmpty() && decoded.size == values.size()) { "INVALID_ARGUMENTS: target values" }
                    decoded.values.forEach { require(it.data !is String || it.data.length <= 1024) }
                } else {
                    val template = legacyTemplate()
                    keys(input, template.keySet() + setOf(ImsModifier.BUNDLE_SELECT_SIM_ID, ImsModifier.BUNDLE_RESET))
                    target(input, ImsModifier.BUNDLE_SELECT_SIM_ID, true)
                    input.keySet().forEach { key ->
                        val value = input.get(key)
                        when (key) {
                            ImsModifier.BUNDLE_SELECT_SIM_ID -> require(value is Int)
                            ImsModifier.BUNDLE_RESET -> require(value is Boolean)
                            else -> {
                                val expected = template.get(key)
                                require(value != null && expected != null && value.javaClass == expected.javaClass) { "INVALID_ARGUMENTS: value type" }
                                require(value !is String || value.length <= 1024)
                                require(value !is IntArray || value.size <= 32)
                            }
                        }
                    }
                }
            }
        }
        val selected = targetSubId(operation, result)
        require(identity == null || operation.isWrite && selected >= 0 && identities == null) { "INVALID_ARGUMENTS: identity scope" }
        require(identities == null || operation.isWrite && selected == -1) { "INVALID_ARGUMENTS: identities scope" }
        require(IdentityRequirementPolicy.valid(requireCapturedIdentity,
            operation.isWrite && operation !in setOf(OperationType.WRITE_CAPTIVE_PORTAL, OperationType.RESET_CAPTIVE_PORTAL),
            selected, identity != null || input.containsKey(TargetConfigProtocol.IDENTITY), identities != null)) {
            "INVALID_ARGUMENTS: missing target identity"
        }
        if (identity != null) result.putString(BridgeProtocol.TARGET_IDENTITY, identity)
        if (identities != null) result.putBundle(BridgeProtocol.TARGET_IDENTITIES, Bundle(identities))
        return result
    }
    fun targetSubId(operation: OperationType, args: Bundle): Int = when (operation) {
        OperationType.READ_CONFIG -> args.getInt(TargetConfigProtocol.SUB_ID, -1)
        OperationType.APPLY_CONFIG, OperationType.BROKER_CONFIG -> args.getInt(if (args.containsKey(TargetConfigProtocol.ACTION)) TargetConfigProtocol.SUB_ID else "select_sim_id", -1)
        OperationType.READ_PERSISTENT_VOLTE, OperationType.SET_PERSISTENT_VOLTE, OperationType.RESTORE_PERSISTENT_VOLTE -> args.getInt(Volte.SUB_ID, -1)
        else -> args.getInt("select_sim_id", -1)
    }
    private fun keys(input: Bundle, allowed: Set<String>) { require(input.keySet().all { it in allowed }) { "INVALID_ARGUMENTS: unknown key" } }
    @Suppress("DEPRECATION")
    private fun target(input: Bundle, key: String, all: Boolean) {
        require(input.get(key) is Int && input.getInt(key) >= if (all) -1 else 0) { "INVALID_ARGUMENTS: target" }
    }
    private fun legacyTemplate(): Bundle = ImsModifier.buildBundle("carrier", "agent", true, true, true, true, true, true, true, true, true, true, true, true, true)
}
