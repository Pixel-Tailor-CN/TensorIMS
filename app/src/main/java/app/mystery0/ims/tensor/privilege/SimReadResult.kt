package app.mystery0.ims.tensor.privilege

/** 成功的空列表与未知/失败结果分离，调用者不能据失败结果切换到“所有 SIM”。 */
data class SimReadResult<T>(val sims: List<T>, val error: String? = null) {
    companion object {
        fun <T> fromPayload(sims: List<T>?, error: String? = null): SimReadResult<T> = when {
            error != null -> SimReadResult(emptyList(), error)
            sims == null -> SimReadResult(emptyList(), "No complete SIM result")
            else -> SimReadResult(sims)
        }
    }
}
