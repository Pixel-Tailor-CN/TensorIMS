package app.mystery0.ims.tensor

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import app.mystery0.ims.tensor.model.LogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LogcatRepository {
    private const val TAG = "LogcatRepository"
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> = _logs

    /** 在主线程获取快照，避免后台导出与采集同时迭代 Compose 列表。 */
    fun snapshot(): List<LogEntry> = _logs.toList()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private var automatic = false
    private var viewers = 0
    private class Capture {
        var process: Process? = null
        var job: Job? = null
    }
    private var capture: Capture? = null
    private val cursor = LogReadCursor()

    fun isCapturing(): Boolean = synchronized(lock) { capture != null }

    fun setAutomaticCapture(enabled: Boolean) = synchronized(lock) {
        automatic = enabled
        reconcile()
    }

    fun openViewer() = synchronized(lock) {
        viewers++
        reconcile()
    }

    fun closeViewer() = synchronized(lock) {
        viewers = (viewers - 1).coerceAtLeast(0)
        reconcile()
    }

    /** 锁内切换会话，旧进程的退出不能清理随后启动的新会话。 */
    private fun reconcile() {
        if (!automatic && viewers == 0) {
            stopCapture()
            return
        }
        if (capture != null) return
        val session = Capture()
        capture = session
        session.job = repositoryScope.launch {
            while (isActive) {
                var process: Process? = null
                try {
                    Log.i(TAG, "Starting logcat capture")
                    // 清空显示不清空游标，正常重启和异常重连均从已消费位置继续。
                    val resume = synchronized(lock) { cursor.resume() }
                    val args = mutableListOf("logcat", "-v", "threadtime", "-v", "usec")
                    resume.timestamp?.let { args.addAll(listOf("-T", it)) }
                    process = ProcessBuilder(args).redirectErrorStream(true).start()
                    val owned = synchronized(lock) {
                        if (capture === session) {
                            session.process = process
                            true
                        } else false
                    }
                    if (!owned) break
                    process.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (!resume.accept(line)) continue
                            val entry = LogEntry.parseLog(line)
                            withContext(Dispatchers.Main) {
                                val owned = synchronized(lock) {
                                    if (capture === session) { cursor.record(line); true } else false
                                }
                                if (owned) {
                                    _logs.add(entry)
                                    if (_logs.size > 2000) _logs.removeAt(0)
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Logcat capture failed", error)
                } finally {
                    process?.destroy()
                    synchronized(lock) {
                        if (session.process === process) session.process = null
                    }
                }
                // 有采集需求时恢复意外退出的 logcat；关闭开关会取消此等待。
                delay(1000)
            }
        }
    }

    private fun stopCapture() {
        val previous = capture ?: return
        capture = null
        previous.job?.cancel()
        previous.process?.destroy()
    }

    fun clearLogs() { _logs.clear() }

    fun stopAndClear() = synchronized(lock) {
        automatic = false
        viewers = 0
        stopCapture()
    }
}
