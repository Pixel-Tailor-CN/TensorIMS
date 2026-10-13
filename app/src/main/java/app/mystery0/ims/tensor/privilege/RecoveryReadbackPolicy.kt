package app.mystery0.ims.tensor.privilege

/** IMS 注册诊断不参与持久值核对；关键值缺失或其他错误仍须阻止解除保护。 */
internal object RecoveryReadbackPolicy {
    fun persistentVolteConfirmed(
        cleanupConfirmed: Boolean,
        unsupported: Boolean,
        hasCriticalValues: Boolean,
        error: String?,
        diagnosticWarning: String?,
    ): Boolean = cleanupConfirmed && (unsupported ||
        (hasCriticalValues && (error == null || (diagnosticWarning != null && error == diagnosticWarning))))
}
