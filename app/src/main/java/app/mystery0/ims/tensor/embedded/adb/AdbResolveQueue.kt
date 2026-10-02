package app.mystery0.ims.tensor.embedded.adb

/**
 * 旧版 NsdManager 同时只允许一个 resolve；取消逻辑请求不能提前释放系统解析槽。
 * 未更新到 T 扩展 7 的 Android 13 没有 stopServiceResolution，必须等旧回调结束。
 * 所有入口及后端回调必须在同一调度线程执行。
 */
internal class AdbResolveQueue<T>(
    private val start: (T, (Result<T>) -> Unit) -> DiscoveryCancellation,
) {
    private class Request<T>(val value: T, var result: ((Result<T>) -> Unit)?) {
        var handle: DiscoveryCancellation? = null
    }

    private val pending = ArrayDeque<Request<T>>()
    private var current: Request<T>? = null

    fun enqueue(value: T, result: (Result<T>) -> Unit): DiscoveryCancellation {
        val request = Request(value, result)
        pending.addLast(request)
        drain()
        return DiscoveryCancellation {
            if (request.result == null) return@DiscoveryCancellation
            request.result = null
            if (current === request) request.handle?.cancel() else pending.remove(request)
        }
    }

    private fun drain() {
        if (current != null || pending.isEmpty()) return
        val request = pending.removeFirst()
        current = request
        try {
            val handle = start(request.value) { value ->
                if (current !== request) return@start
                current = null
                val callback = request.result
                request.result = null
                callback?.invoke(value)
                drain()
            }
            // 后端可能同步报告失败；不能把其句柄写入下一轮请求。
            if (current === request) request.handle = handle
        } catch (failure: Exception) {
            if (current === request) {
                current = null
                val callback = request.result
                request.result = null
                callback?.invoke(Result.failure(failure))
                drain()
            }
        }
    }
}
