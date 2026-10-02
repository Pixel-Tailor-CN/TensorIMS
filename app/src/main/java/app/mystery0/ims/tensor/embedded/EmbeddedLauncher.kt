package app.mystery0.ims.tensor.embedded

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import app.mystery0.ims.tensor.R
import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.bridge.BridgeProtocol
import app.mystery0.ims.tensor.embedded.adb.AdbIdentity
import app.mystery0.ims.tensor.embedded.adb.AdbIdentityUnavailableException
import app.mystery0.ims.tensor.embedded.adb.AdbPairingClient
import app.mystery0.ims.tensor.embedded.adb.AdbProtocolException
import app.mystery0.ims.tensor.embedded.adb.AdbTlsClient
import app.mystery0.ims.tensor.embedded.adb.AdbServiceRejectedException
import app.mystery0.ims.tensor.embedded.adb.BootstrapCommand
import app.mystery0.ims.tensor.embedded.adb.InvalidPairingCodeException
import app.mystery0.ims.tensor.embedded.adb.LaunchInput
import app.mystery0.ims.tensor.embedded.adb.LoopbackConnection
import app.mystery0.ims.tensor.embedded.adb.PairingStage
import app.mystery0.ims.tensor.embedded.adb.withAdbNetworkResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext

/** 仅由用户明确选择的内置模式动作调用；调用方须持有 PrivilegeRuntime 的启动/切换安全门。 */
class EmbeddedLauncher(context: Context) {
    private val context = context.applicationContext

    suspend fun pair(port: Int, code: String): String? {
        if (!LaunchInput.validPort(port)) return context.getString(R.string.launcher_pair_port)
        if (!LaunchInput.validPairCode(code)) return context.getString(R.string.launcher_pair_code)
        val pairingStage = AtomicReference(PairingStage.IDENTITY)
        return exclusive("pairing", { pairingStage.get().diagnosticName }) {
            network(port) { connection ->
                val identity = AdbIdentity.load(context, pairing = true)
                AdbPairingClient(connection, identity).pair(code) { pairingStage.set(it) }
            }
            null
        }
    }

    suspend fun startWireless(port: Int): String? {
        if (!LaunchInput.validPort(port)) return context.getString(R.string.launcher_connect_port)
        return exclusive("wireless-start") {
            start { command ->
                network(port) { connection ->
                    AdbTlsClient(connection, AdbIdentity.load(context, pairing = false)).bootstrap(command)
                }
            }
        }
    }

    suspend fun startRoot(): String? = exclusive("root-start") {
        start { command ->
            withContext(Dispatchers.IO) {
                // 仅执行固定当前 APK 的启动命令；不探测、不停止任何其他提权服务。
                val process = ProcessBuilder("su", "-c", command)
                    .redirectOutput(File("/dev/null")).redirectError(File("/dev/null")).start()
                try {
                    process.outputStream.close()
                    withTimeout(20000) {
                        while (process.isAlive) delay(100)
                        if (process.exitValue() != 0) throw RootRejectedException()
                    }
                } finally {
                    // 这里只回收本次 su 客户端，绝不按 PID 文件或进程名终止后台服务。
                    if (process.isAlive) process.destroy()
                }
            }
        }
    }

    private suspend fun start(execute: suspend (String) -> Unit): String? {
        // 已认证实例由运行时负责连接/停止；不生成第二个后台服务。
        if (EmbeddedConnection.current() != null) return context.getString(R.string.launcher_connected)
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes).hex()
        val command = withContext(Dispatchers.IO) { installedCommand(challenge) }
        coroutineContext.ensureActive()
        EmbeddedConnection.expect(challenge)
        var accepted = false
        try {
            try {
                execute(command)
            } catch (failure: Exception) {
                coroutineContext.ensureActive()
                // ADB 通道在后台进程已认证后断开，不应把已证实连接误报为失败。
                if (EmbeddedConnection.acceptedChallenge() == challenge && EmbeddedConnection.current() != null) {
                    accepted = true
                    return null
                }
                throw failure
            }
            try {
                withTimeout(15000) {
                    while (EmbeddedConnection.acceptedChallenge() != challenge || EmbeddedConnection.current() == null) delay(100)
                }
            } catch (timeout: TimeoutCancellationException) {
                coroutineContext.ensureActive()
                return context.getString(R.string.launcher_handshake)
            }
            accepted = true
            return null
        } finally {
            // 超时与取消均撤销挑战，迟到交付不能被下一次启动误认成功。
            if (!accepted) EmbeddedConnection.clear()
        }
    }

    private fun installedCommand(challenge: String): String {
        check(context.packageName == BridgeProtocol.PACKAGE)
        val installed = context.packageManager.getPackageInfo(BridgeProtocol.PACKAGE,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        val app = checkNotNull(installed.applicationInfo)
        check(app.uid == Process.myUid())
        check(installed.longVersionCode == BuildConfig.VERSION_CODE.toLong() && installed.versionName == BuildConfig.VERSION_NAME)
        val apk = File(app.sourceDir)
        check(apk.isFile && apk.canRead() && apk.length() > 0 && apk.canonicalPath == app.sourceDir)
        val signers = checkNotNull(installed.signingInfo).apkContentsSigners
        check(signers.size == 1)
        val signer = MessageDigest.getInstance("SHA-256").digest(signers.single().toByteArray()).hex()
        return BootstrapCommand.create(app.uid / 100000, app.uid, installed.longVersionCode, signer, challenge, app.sourceDir)
    }

    private suspend fun <T> network(port: Int, block: (LoopbackConnection) -> T): T {
        val connection = LoopbackConnection(port)
        return withAdbNetworkResource(connection) { block(connection) }
    }

    private suspend fun exclusive(stage: String, diagnostic: () -> String? = { null }, action: suspend () -> String?): String? {
        if (!launchMutex.tryLock()) return context.getString(R.string.launcher_busy)
        fun detail(message: String, failure: Throwable): String {
            val position = diagnostic() ?: return message
            return message + "\n" + context.getString(R.string.adb_pair_diagnostic, position, failure.javaClass.simpleName)
        }
        try {
            return action()
        } catch (failure: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            Log.w(TAG, "Embedded launch timed out: $stage/${diagnostic().orEmpty()}")
            return detail(context.getString(R.string.launcher_timeout), failure)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // 异常内容可能包含路径/密钥材料，日志仅记录阶段与类型；不输出完整堆栈。
            Log.w(TAG, "Embedded launch failed: $stage/${diagnostic().orEmpty()} (${failure.javaClass.simpleName})")
            val message = when (failure) {
                is AdbIdentityUnavailableException -> context.getString(R.string.launcher_identity)
                is InvalidPairingCodeException -> context.getString(R.string.launcher_pair_failed)
                is RootRejectedException -> context.getString(R.string.launcher_root_failed)
                is AdbServiceRejectedException -> context.getString(R.string.adb_service_rejected)
                is AdbProtocolException -> context.getString(R.string.launcher_protocol)
                is java.net.SocketTimeoutException -> context.getString(R.string.launcher_socket_timeout)
                is java.net.ConnectException -> context.getString(R.string.launcher_connect_failed)
                else -> context.getString(R.string.launcher_failed, stage)
            }
            return detail(message, failure)
        } catch (failure: LinkageError) {
            Log.w(TAG, "Native pairing unavailable: ${diagnostic().orEmpty()}")
            return detail(context.getString(R.string.launcher_native), failure)
        } finally {
            launchMutex.unlock()
        }
    }

    companion object {
        private const val TAG = "EmbeddedLauncher"
        private val launchMutex = Mutex()
    }
}
private class RootRejectedException : java.io.IOException("Root command rejected")
private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }
