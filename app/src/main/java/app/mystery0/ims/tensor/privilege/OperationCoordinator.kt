package app.mystery0.ims.tensor.privilege

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** 业务记录、后端执行、fallback、模式切换及关闭共用同一把锁。 */
object OperationCoordinator {
    private val mutex = Mutex()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _completionVersion = MutableStateFlow(0L)
    val completionVersion = _completionVersion.asStateFlow()

    private class Owner : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Owner>
    }

    suspend fun <T> serialized(block: suspend () -> T): T {
        if (coroutineContext[Owner] != null) return block()
        mutex.lock()
        return owned(block)
    }

    /** 切换和启动不进入队列，避免用户离开界面后发生迟到的模式变更。 */
    suspend fun <T> tryExclusive(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        return owned(block)
    }

    private suspend fun <T> owned(block: suspend () -> T): T {
        _busy.value = true
        return try {
            withContext(NonCancellable + Owner()) { block() }
        } finally {
            // 快速任务的 busy=true/false 可能被 StateFlow 合并；完成代际确保闲置计时仍能重置。
            _completionVersion.value += 1
            _busy.value = false
            mutex.unlock()
        }
    }
}
