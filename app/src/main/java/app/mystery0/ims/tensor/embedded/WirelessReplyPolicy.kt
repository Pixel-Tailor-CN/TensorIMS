package app.mystery0.ims.tensor.embedded

/** 通知验证码仅在当前会话的等待阶段接收；旧通知、重复提交及无效字符均不能发起网络操作。 */
internal object WirelessReplyPolicy {
    fun accepts(session: String?, receivedSession: String?, phase: WirelessAdbPhase, code: String?): Boolean =
        session != null && session == receivedSession && phase == WirelessAdbPhase.WAITING_CODE &&
            code != null && code.length == 6 && code.all { it in '0'..'9' }
}
