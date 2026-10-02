package app.mystery0.ims.tensor.bridge

object BridgeProtocol {
    const val VERSION = 1
    const val PACKAGE = "app.mystery0.ims.tensor"
    const val AUTHORITY = "$PACKAGE.embedded.bridge"
    const val PROVIDER_METHOD = "deliver"
    const val PREFIX = "tensorims.bridge."
    const val PROTOCOL = PREFIX + "protocol"
    const val CHALLENGE = PREFIX + "challenge"
    const val SERVER = PREFIX + "server"
    const val SESSION = PREFIX + "session"
    const val OPERATION_ID = PREFIX + "operationId"
    const val EPOCH = PREFIX + "epoch"
    const val OPERATION = PREFIX + "operation"
    const val TARGET_IDENTITY = PREFIX + "targetIdentity"
    const val TARGET_IDENTITIES = PREFIX + "targetIdentities"
    const val ARGS = PREFIX + "args"
    const val INSTANCE_ID = PREFIX + "instanceId"
    const val RUNTIME_UID = PREFIX + "runtimeUid"
    const val VERSION_CODE = PREFIX + "versionCode"
    const val SIGNER = PREFIX + "signer"
    const val APK_PATH = PREFIX + "apkPath"
    const val CAPABILITIES = PREFIX + "capabilities"
    const val EXPIRES_AT = PREFIX + "expiresAt"
    const val CLEANUP_CONFIRMED = PREFIX + "cleanupConfirmed"
    const val VERIFIED_SUB_IDS = PREFIX + "verifiedSubIds"
    const val OBSERVED_CONFIGS = PREFIX + "observedConfigs"
    const val DIAGNOSTIC_WARNING = PREFIX + "diagnosticWarning"
    const val BROKER_RETRY_ALLOWED = PREFIX + "brokerRetryAllowed"
    const val ERROR_CODE = PREFIX + "errorCode"
    const val MESSAGE = PREFIX + "message"
    const val LEASE_MS = 30_000L
    const val REQUEST_MS = 120_000L
}
