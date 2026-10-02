package app.mystery0.ims.tensor.privilege

/** 固定操作映射由应用和服务共同使用，任何调用者都不能指定任意组件。 */
enum class OperationType(val instrumentationClassName: String, val isWrite: Boolean) {
    READ_SIMS("SimReader", false),
    READ_CAPABILITIES("ImsCapabilityReader", false),
    READ_CONFIG("ImsConfigurationReader", false),
    APPLY_CONFIG("ImsModifier", true),
    BROKER_CONFIG("BrokerInstrumentation", true),
    RESET_IMS("ImsResetter", true),
    READ_PERSISTENT_VOLTE("PersistentVolteModifier", false),
    SET_PERSISTENT_VOLTE("PersistentVolteModifier", true),
    RESTORE_PERSISTENT_VOLTE("PersistentVolteModifier", true),
    READ_CAPTIVE_PORTAL("CaptivePortalSettingsModifier", false),
    WRITE_CAPTIVE_PORTAL("CaptivePortalSettingsModifier", true),
    RESET_CAPTIVE_PORTAL("CaptivePortalSettingsModifier", true);

    val componentClassName: String get() = "app.mystery0.ims.tensor.privileged.$instrumentationClassName"
}
