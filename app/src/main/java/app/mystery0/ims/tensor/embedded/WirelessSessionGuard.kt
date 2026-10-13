package app.mystery0.ims.tensor.embedded

/** 进程级通知/界面归属；旧 Service 的清理和超时不能结束已经替换的新请求。 */
internal class WirelessSessionGuard {
    @Volatile var current: String? = null
        private set
    private var automaticId: String? = null

    @Synchronized fun begin(token: String, automaticRequestId: String? = null) { current = token; automaticId = automaticRequestId }
    @Synchronized fun ownsAutomatic(requestId: String): Boolean = current != null && automaticId == requestId
    @Synchronized fun tryBegin(token: String, automaticRequestId: String? = null): Boolean {
        if (current != null) return false
        begin(token, automaticRequestId)
        return true
    }
    @Synchronized fun owns(token: String?) = token != null && current == token
    @Synchronized fun end(token: String?): Boolean {
        if (!owns(token)) return false
        current = null
        automaticId = null
        return true
    }
}
