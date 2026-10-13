package app.mystery0.ims.tensor.privilege

internal class EmbeddedForegroundTracker {
    companion object { const val BACKGROUND_DELAY_MS = 700L }
    private val activities = mutableSetOf<Any>()
    private val resumedMain = mutableSetOf<Any>()
    private var backgroundDeadline: Long? = null
    private var enableRevision = 0L
    private var consumed: Pair<Long, Long>? = null
    @Volatile var generation = 0L
        private set
    @Volatile var isForeground = false
        private set
    val hasVisibleActivity: Boolean @Synchronized get() = activities.isNotEmpty()
    val hasResumedMain: Boolean @Synchronized get() = resumedMain.isNotEmpty()

    @Synchronized fun started(activity: Any, now: Long) {
        confirmBackground(now)
        if (!isForeground) { isForeground = true; generation++ }
        activities += activity
        backgroundDeadline = null
    }

    @Synchronized fun stopped(activity: Any, changingConfigurations: Boolean, now: Long) {
        activities -= activity
        resumedMain -= activity
        // 配置重建可能超过去抖延迟，必须保留同一代际而非只依赖计时。
        if (activities.isEmpty() && !changingConfigurations) backgroundDeadline = now + BACKGROUND_DELAY_MS
    }

    @Synchronized fun mainResumed(activity: Any) { resumedMain += activity }
    @Synchronized fun mainPaused(activity: Any) { resumedMain -= activity }
    @Synchronized fun confirmBackground(now: Long): Boolean {
        val deadline = backgroundDeadline ?: return false
        if (activities.isNotEmpty() || now < deadline) return false
        backgroundDeadline = null
        isForeground = false
        return true
    }

    @Synchronized fun explicitEnable() { enableRevision++ }
    @Synchronized fun suppress() { consumed = generation to enableRevision }
    @Synchronized fun pending(): Boolean = isForeground && resumedMain.isNotEmpty() && consumed != generation to enableRevision
    @Synchronized fun consume(): Boolean {
        if (!pending()) return false
        suppress()
        return true
    }
}
