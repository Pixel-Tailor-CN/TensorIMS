package app.mystery0.ims.tensor.privilege

/** 闲置停止只用于本包内置服务；输入边界同时供界面和偏好存储使用。 */
object EmbeddedIdleStopPolicy {
    const val DEFAULT_MINUTES = 2
    const val MAX_MINUTES = 30

    fun parseMinutes(text: String): Int? = text.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }
        ?.toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }

    fun canSchedule(enabled: Boolean, foreground: Boolean, taskActive: Boolean, status: BackendStatus): Boolean =
        enabled && !foreground && !taskActive && status.mode == BackendMode.EMBEDDED && status.isReady
}
