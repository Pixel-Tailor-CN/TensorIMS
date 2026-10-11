package app.mystery0.ims.tensor.embedded

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.util.Log
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.bridge.ComputerBootstrapIdentity
import app.mystery0.ims.tensor.bridge.InstalledIdentity
import org.lsposed.hiddenapibypass.LSPass
import java.io.File

/** 原生启动器固定调用的电脑启动入口；命令行只传用户与自身安装路径。 */
object ComputerBootstrapMain {
    @Suppress("DEPRECATION")
    @JvmStatic fun main(args: Array<String>) {
        var stage = "uid"
        try {
            require(Process.myUid() == 0 || Process.myUid() == 2000) { "Unsupported runtime UID" }
            require(args.size == 3) { "Invalid computer bootstrap arguments" }
            val userId = args[0].toIntOrNull()
            require(userId != null && userId in 0..(Int.MAX_VALUE / 100000)) { "Invalid Android user" }
            // app_process 启动需要反射 ActivityThread 与目标用户 Context 的 Android 13+ 隐藏 API。
            LSPass.setHiddenApiExemptions("")
            Looper.prepareMainLooper()
            stage = "context"
            val context = BootstrapPlatform.systemContextForUser(userId)
            stage = "installation"
            val installation = validateInstallation(context, userId, args[1], args[2])
            check(System.getProperty("java.class.path").orEmpty().split(':').contains(installation.identity.apkPath)) {
                "APK classpath mismatch"
            }
            stage = "claim"
            val reply = BootstrapPlatform.callProvider(
                BridgeProtocol.COMPUTER_CLAIM_METHOD,
                Bundle(),
                userId,
            )
            check(reply.getBoolean("accepted")) { "Computer bootstrap claim rejected" }
            val grant = ComputerBootstrapGrant(
                challenge = requireNotNull(reply.getString(BridgeProtocol.CHALLENGE)),
                generation = reply.getLong(BridgeProtocol.COMPUTER_GENERATION, -1L),
                epoch = reply.getLong(BridgeProtocol.EPOCH, -1L),
                identityDigest = requireNotNull(reply.getString(BridgeProtocol.COMPUTER_IDENTITY)),
                callerUid = reply.getInt(BridgeProtocol.RUNTIME_UID, -1),
                userId = userId,
            )
            require(grant.generation >= 0 && grant.epoch >= 0 && grant.callerUid in setOf(0, 2000)) {
                "Invalid computer bootstrap grant"
            }
            check(grant.identityDigest == installation.digest) { "Computer installation changed" }
            stage = "server"
            EmbeddedServerMain.runAuthenticatedBootstrap(context, installation.identity, grant.challenge) { server ->
                deliver(server, grant)
            }
        } catch (failure: Throwable) {
            val cause = generateSequence(failure as Throwable?) { it.cause }.last()
            val phase = (failure as? BootstrapPlatformFailure)?.stage?.let { "/$it" }.orEmpty()
            Log.e("TensorIMSBridge", "Computer bootstrap failed at $stage$phase (${cause.javaClass.simpleName})")
            kotlin.system.exitProcess(1)
        }
    }

    private fun validateInstallation(
        context: Context,
        userId: Int,
        launcherPath: String,
        apkPath: String,
    ): InstalledComputerIdentity {
        val identity = InstalledIdentity.read(context)
        val app = context.packageManager.getApplicationInfo(
            BridgeProtocol.PACKAGE,
            PackageManager.ApplicationInfoFlags.of(0),
        )
        val canonicalApk = File(app.sourceDir).canonicalPath
        val canonicalLibrary = File(app.nativeLibraryDir).canonicalPath
        val canonicalLauncher = File(canonicalLibrary, "libtensorims_starter.so").canonicalPath
        check(identity.userId == userId && identity.versionCode == BuildConfig.VERSION_CODE.toLong()) {
            "Installation identity mismatch"
        }
        check(identity.apkPath == canonicalApk && apkPath == canonicalApk && launcherPath == canonicalLauncher) {
            "Installed launcher path mismatch"
        }
        check(File(canonicalApk).isFile && File(canonicalLauncher).isFile) { "Installed launcher unavailable" }
        return InstalledComputerIdentity(identity, ComputerBootstrapIdentity.digest(identity, canonicalLibrary))
    }

    private fun deliver(server: EmbeddedServer, grant: ComputerBootstrapGrant) {
        val extras = Bundle().apply {
            putString(BridgeProtocol.CHALLENGE, grant.challenge)
            putBinder(BridgeProtocol.SERVER, server)
            putLong(BridgeProtocol.COMPUTER_GENERATION, grant.generation)
            putLong(BridgeProtocol.EPOCH, grant.epoch)
            putString(BridgeProtocol.COMPUTER_IDENTITY, grant.identityDigest)
            putInt(BridgeProtocol.RUNTIME_UID, grant.callerUid)
        }
        val reply = BootstrapPlatform.callProvider(
            BridgeProtocol.COMPUTER_DELIVER_METHOD,
            extras,
            grant.userId,
        )
        check(reply.getBoolean("accepted")) { "Provider rejected computer server" }
    }

    private data class InstalledComputerIdentity(
        val identity: InstalledIdentity,
        val digest: String,
    )

    private data class ComputerBootstrapGrant(
        val challenge: String,
        val generation: Long,
        val epoch: Long,
        val identityDigest: String,
        val callerUid: Int,
        val userId: Int,
    )
}
