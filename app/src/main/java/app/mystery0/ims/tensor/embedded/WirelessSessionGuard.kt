package app.mystery0.ims.tensor.embedded

/** 进程级通知/界面归属；旧 Service 的清理和超时不能结束已经替换的新请求。 */
internal class WirelessSessionGuard {
    @Volatile var current: String? = null
        private set

    @Synchronized fun begin(token: String) { current = token }
    @Synchronized fun owns(token: String?) = token != null && current == token
    @Synchronized fun end(token: String?): Boolean {
        if (!owns(token)) return false
        current = null
        return true
    }
}
