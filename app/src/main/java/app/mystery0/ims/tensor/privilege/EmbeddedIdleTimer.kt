package app.mystery0.ims.tensor.privilege

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** 每次资格或设置变化均撤销旧等待；计时结束只提出请求，最终停止仍需 Runtime 安全检查。 */
class EmbeddedIdleTimer(private val wait: suspend (Long) -> Unit = { delay(it) }) {
    data class Request(val minutes: Int, val epoch: Long, val completionVersion: Long = 0)

    suspend fun observe(requests: Flow<Request?>, onTimeout: suspend (Request) -> Unit) {
        requests.collectLatest { request ->
            if (request == null) return@collectLatest
            require(request.minutes in 1..EmbeddedIdleStopPolicy.MAX_MINUTES)
            wait(request.minutes * 60_000L)
            onTimeout(request)
        }
    }
}
