package app.mystery0.ims.tensor.embedded.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable

/** 超时/取消先关闭资源解除阻塞，再等待工作线程和 JNI 清理；不能反过来先等待线程。 */
internal suspend fun <T> withAdbNetworkResource(resource: Closeable, timeoutMillis: Long = 30000,
                                               block: () -> T): T = coroutineScope {
    val work = async(Dispatchers.IO) { block() }
    try {
        withTimeout(timeoutMillis) { work.await() }
    } finally {
        resource.close()
        withContext(NonCancellable) { work.join() }
    }
}
