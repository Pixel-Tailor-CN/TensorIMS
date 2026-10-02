package app.mystery0.ims.tensor.embedded.adb

internal object BootstrapCommand {
    private val hex = Regex("[0-9a-f]{64}")
    fun create(userId: Int, uid: Int, version: Long, signer: String, challenge: String, apk: String): String {
        require(userId >= 0 && uid >= 10000 && uid / 100000 == userId && version > 0)
        require(hex.matches(signer) && hex.matches(challenge))
        require(apk.startsWith("/data/app/") && apk.endsWith(".apk") && apk.none { it.code < 32 || it.code == 127 })
        require(apk.split('/').none { it == "." || it == ".." })
        // 路径由 PackageManager 提供仍按 shell 单参数转义；不接受任意类名或命令。
        val args = listOf(userId.toString(), uid.toString(), version.toString(), signer, challenge, apk).joinToString(" ", transform = ::quote)
        return "CLASSPATH=${quote(apk)} /system/bin/setsid /system/bin/app_process /system/bin --nice-name=tensorims_embedded " +
            "app.mystery0.ims.tensor.embedded.EmbeddedServerMain $args </dev/null >/dev/null 2>&1 &"
    }
    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
}
