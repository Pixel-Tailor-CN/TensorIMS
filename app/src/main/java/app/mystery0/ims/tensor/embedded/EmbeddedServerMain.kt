package app.mystery0.ims.tensor.embedded

import android.app.IActivityManager
import android.content.AttributionSource
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.util.Log
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import org.lsposed.hiddenapibypass.LSPass

/** 由当前安装 APK 的 app_process classpath 加载；不依赖官方管理器或授权数据库。 */
object EmbeddedServerMain {
    @Suppress("DEPRECATION")
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(Process.myUid() == 0 || Process.myUid() == 2000) { "Unsupported runtime UID" }
            require(args.size == 6) { "Invalid bootstrap arguments" }
            val userId = args[0].toInt(); val uid = args[1].toInt(); val version = args[2].toLong()
            require(userId >= 0 && uid / 100000 == userId && args[4].matches(Regex("[a-f0-9]{64}")))
            LSPass.setHiddenApiExemptions("")
            Looper.prepareMainLooper()
            val context = systemContextForUser(userId)
            val identity = InstalledIdentity.read(context)
            require(identity.uid == uid && identity.userId == userId && identity.versionCode == version &&
                identity.signer == args[3] && identity.apkPath == args[5] && version == BuildConfig.VERSION_CODE.toLong()) {
                "Installation identity mismatch"
            }
            require(System.getProperty("java.class.path").orEmpty().split(':').contains(identity.apkPath)) { "APK classpath mismatch" }
            val server = EmbeddedServer(context, identity, args[4]) { kotlin.system.exitProcess(0) }
            deliver(server, args[4], userId)
            val handler = Handler(Looper.getMainLooper())
            val leaseCheck = object : Runnable {
                override fun run() { server.checkIdleLease(); handler.postDelayed(this, 1000) }
            }
            handler.postDelayed(leaseCheck, 1000)
            Log.i("TensorIMSBridge", "Private server started")
            Looper.loop()
        } catch (failure: Throwable) {
            // 不回显启动参数，挑战与证书参数不能进入日志。
            Log.e("TensorIMSBridge", "Private server bootstrap failed", failure)
            kotlin.system.exitProcess(1)
        }
    }
    private fun systemContextForUser(userId: Int): Context {
        // app_process 没有 Application；ActivityThread 系统 Context 与目标 UserHandle 均来自 framework。
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getMethod("systemMain").invoke(null)
        val context = threadClass.getMethod("getSystemContext").invoke(thread) as Context
        val user = UserHandle::class.java.getDeclaredMethod("of", Int::class.javaPrimitiveType).invoke(null, userId)
        return Context::class.java.getMethod("createContextAsUser", UserHandle::class.java, Int::class.javaPrimitiveType)
            .invoke(context, user, 0) as Context
    }
    private fun deliver(server: EmbeddedServer, challenge: String, userId: Int) {
        val manager = IActivityManager.Stub.asInterface(requireNotNull(ServiceManager.getService(Context.ACTIVITY_SERVICE)))
        val authority = BridgeProtocol.AUTHORITY
        var acquired = false
        try {
            // Android 13+ 隐藏 API；直接定向获取单个 Provider，不扫描包、不广播、不 force-stop。
            val holder = manager.javaClass.getMethod("getContentProviderExternal", String::class.java,
                Int::class.javaPrimitiveType, IBinder::class.java, String::class.java)
                .invoke(manager, authority, userId, null, "TensorIMS bootstrap") ?: error("Provider unavailable")
            acquired = true
            val provider = requireNotNull(holder.javaClass.getField("provider").get(holder))
            val extras = Bundle().apply {
                putString(BridgeProtocol.CHALLENGE, challenge)
                putBinder(BridgeProtocol.SERVER, server)
            }
            val source = AttributionSource.Builder(Process.myUid()).build()
            val reply = provider.javaClass.getMethod("call", AttributionSource::class.java, String::class.java,
                String::class.java, String::class.java, Bundle::class.java)
                .invoke(provider, source, authority, BridgeProtocol.PROVIDER_METHOD, null, extras) as? Bundle
            check(reply?.getBoolean("accepted") == true) { "Provider rejected bootstrap" }
        } finally {
            if (acquired) runCatching {
                manager.javaClass.getMethod("removeContentProviderExternalAsUser", String::class.java,
                    IBinder::class.java, Int::class.javaPrimitiveType).invoke(manager, authority, null, userId)
            }
        }
    }
}
