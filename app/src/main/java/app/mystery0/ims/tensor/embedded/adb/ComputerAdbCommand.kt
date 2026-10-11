package app.mystery0.ims.tensor.embedded.adb

/** 构造只引用系统安装目录固定启动器的电脑 ADB 命令。 */
internal object ComputerAdbCommand {
    private val installedStarter = Regex(
        "^/data/app/[A-Za-z0-9._=~-]+/(?:[A-Za-z0-9._=~-]+/)?lib/(arm64|x86_64)/libtensorims_starter\\.so$",
    )

    fun create(starterPath: String, userId: Int): String {
        require(installedStarter.matches(starterPath)) { "Invalid installed starter path" }
        require(userId in 0..(Int.MAX_VALUE / ANDROID_USER_OFFSET)) { "Invalid Android user ID" }
        require(starterPath.split('/').none { it == "." || it == ".." })
        return "adb shell $starterPath --user $userId"
    }

    private const val ANDROID_USER_OFFSET = 100000
}
