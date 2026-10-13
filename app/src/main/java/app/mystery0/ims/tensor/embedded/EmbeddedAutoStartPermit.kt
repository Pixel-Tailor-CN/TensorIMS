package app.mystery0.ims.tensor.embedded

internal class EmbeddedAutoStartPermit(
    val requestId: String,
    val epoch: Long,
    private val authorized: () -> Boolean,
    private val foreground: () -> Boolean,
    private val visible: () -> Boolean = foreground,
) {
    @Volatile private var revoked = false
    @Volatile var bootstrapStarted = false
        private set

    /** 已进入引导后，退后台不取消事务；用户撤销许可或 epoch 变化仍拒绝迟到交付。 */
    fun canContinue(): Boolean = !revoked && authorized() && (bootstrapStarted || foreground())

    @Synchronized fun tryBeginBootstrap(): Boolean {
        if (bootstrapStarted || !canContinue() || !visible()) return false
        bootstrapStarted = true
        return true
    }

    @Synchronized fun revoke(beforeBootstrapOnly: Boolean = false): Boolean {
        if (beforeBootstrapOnly && bootstrapStarted) return false
        revoked = true
        return true
    }

    /** 与撤销共用同步边界；只提交本地接纳状态，不持锁执行远端握手。 */
    @Synchronized fun commitDelivery(commit: () -> Boolean): Boolean = canContinue() && commit()
}
