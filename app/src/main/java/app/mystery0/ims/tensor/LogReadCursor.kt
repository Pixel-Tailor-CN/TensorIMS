package app.mystery0.ims.tensor

/** logcat 的 -T 包含起始时间，按边界行的出现次数消除重叠，不删除真实重复事件。 */
internal class LogReadCursor {
    private var timestamp: String? = null
    private val boundary = mutableMapOf<String, Int>()

    fun resume(): Resume = Resume(timestamp, boundary.toMutableMap())

    fun record(line: String) {
        val time = timeOf(line) ?: return
        if (time != timestamp) {
            timestamp = time
            boundary.clear()
        }
        boundary[line] = (boundary[line] ?: 0) + 1
    }

    class Resume internal constructor(val timestamp: String?, private val remaining: MutableMap<String, Int>) {
        fun accept(line: String): Boolean {
            if (line.startsWith("---------")) return false
            if (timeOf(line) != timestamp) return true
            val count = remaining[line] ?: return true
            if (count == 1) remaining.remove(line) else remaining[line] = count - 1
            return false
        }
    }

    companion object {
        private val timePattern = Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+")
        private fun timeOf(line: String): String? = timePattern.find(line)?.value
    }
}
