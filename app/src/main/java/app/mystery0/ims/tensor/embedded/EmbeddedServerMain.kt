package app.mystery0.ims.tensor.embedded

import android.content.Context
import android.os.Looper
import android.os.Process
import android.util.Log
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import org.lsposed.hiddenapibypass.LSPass

/** 由当前安装 APK 的 app_process 加载，不依赖官方管理器或授权数据库。 */
object EmbeddedServerMain {
    @Suppress("DEPRECATION")
    @JvmStatic fun main(args: Array<String>) {
        var stage = "arguments"
        try {
            require(Process.myUid() == 0 || Process.myUid() == 2000) { "Unsupported runtime UID" }
            require(args.size == 6) { "Invalid bootstrap arguments" }
            val userId = args[0].toInt()
            val uid = args[1].toInt()
            val version = args[2].toLong()
            require(userId >= 0 && uid / 100000 == userId && args[4].matches(Regex("[a-f0-9]{64}")))
            stage = "platform"
            // Android 13+ 隐藏 API 兼容入口集中处理，固定服务仍使用既有六参数协议。
            LSPass.setHiddenApiExemptions("")
            Looper.prepareMainLooper()
            val context = BootstrapPlatform.systemContextForUser(userId)
            val identity = InstalledIdentity.read(context)
            require(identity.uid == uid && identity.userId == userId && identity.versionCode == version &&
                identity.signer == args[3] && identity.apkPath == args[5] && version == BuildConfig.VERSION_CODE.toLong()) {
                "Installation identity mismatch"
            }
            require(System.getProperty("java.class.path").orEmpty().split(':').contains(identity.apkPath)) {
                "APK classpath mismatch"
            }
            runAuthenticatedBootstrap(context, identity, args[4]) { server -> deliver(server, args[4], userId) }
        } catch (failure: Throwable) {
            // 不打印启动参数或异常堆栈，避免挑战与安装材料进入日志。
            Log.e("TensorIMSBridge", "Private server bootstrap failed at $stage (${failure.javaClass.simpleName})")
            kotlin.system.exitProcess(1)
        }
    }

    internal fun runAuthenticatedBootstrap(
        context: Context,
        identity: InstalledIdentity,
        challenge: String,
        deliver: (EmbeddedServer) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Bootstrap main looper is not prepared" }
        val server = EmbeddedServer(context, identity, challenge) { kotlin.system.exitProcess(0) }
        deliver(server)
        val handler = android.os.Handler(Looper.getMainLooper())
        val leaseCheck = object : Runnable {
            override fun run() {
                server.checkIdleLease()
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(leaseCheck, 1000)
        Log.i("TensorIMSBridge", "Private server started")
        Looper.loop()
    }

    private fun deliver(server: EmbeddedServer, challenge: String, userId: Int) {
        val extras = android.os.Bundle().apply {
            putString(BridgeProtocol.CHALLENGE, challenge)
            putBinder(BridgeProtocol.SERVER, server)
        }
        val reply = BootstrapPlatform.callProvider(BridgeProtocol.PROVIDER_METHOD, extras, userId)
        check(reply.getBoolean("accepted")) { "Provider rejected bootstrap" }
    }
}
