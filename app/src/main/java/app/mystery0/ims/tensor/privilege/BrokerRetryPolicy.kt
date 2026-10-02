package app.mystery0.ims.tensor.privilege

/** 只有主写事务明确证明尚未修改任何目标时，才允许同模式 Broker 重试。 */
object BrokerRetryPolicy {
    fun allowed(hasResult: Boolean, explicitlyAllowed: Boolean, errorCode: String?): Boolean =
        hasResult && explicitlyAllowed && errorCode !in setOf("DELEGATION_BUSY", "CLEANUP_FAILED", "OPERATION_INDETERMINATE")
}
