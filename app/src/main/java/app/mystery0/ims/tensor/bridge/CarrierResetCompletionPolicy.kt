package app.mystery0.ims.tensor.bridge

/** 请求完成与覆盖删除证明分开；不能把普通写入或部分失败降为非阻塞提示。 */
object CarrierResetCompletionPolicy {
    const val UNVERIFIED = "RESET_REMOVAL_UNVERIFIED"

    fun completedWithoutRemovalProof(isReset: Boolean, targets: Set<Int>, verified: Set<Int>,
        unverified: Set<Int>, hasFailure: Boolean): Boolean = isReset && !hasFailure &&
        targets.isNotEmpty() && targets.all { it >= 0 } && unverified.isNotEmpty() &&
        verified.intersect(unverified).isEmpty() && verified + unverified == targets
}
