package app.mystery0.ims.tensor.embedded.adb

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.NetworkInterface

/** 每次调用重新发现端口；配对结束后必须独立查找 CONNECT，不能复用配对端口。 */
internal class AdbServiceDiscovery(context: Context) {
    private val context = context.applicationContext

    suspend fun findPort(kind: AdbServiceKind, timeoutMillis: Long = 15_000): Int {
        require(timeoutMillis in 1..120_000)
        return withContext(Dispatchers.Main.immediate) {
            withTimeout(timeoutMillis) {
                val multicast = acquireMulticastLock()
                try {
                    suspendCancellableCoroutine { continuation ->
                        val session = AdbDiscoverySession(kind, driver(context), ::isLocalAddress) {
                            if (continuation.isActive) continuation.resumeWith(it)
                        }
                        continuation.invokeOnCancellation {
                            if (Looper.myLooper() == handler.looper) session.cancel()
                            else handler.post { session.cancel() }
                        }
                        if (continuation.isActive) session.start() else session.cancel()
                    }
                } finally {
                    multicast?.let { if (it.isHeld) it.release() }
                }
            }
        }
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        // 系统设置在前台时，本应用只有 FGS，不能依赖 NSD 的前台 Activity 组播保护。
        // 全版本只在有界发现期间持锁，完成、失败和协程取消均在 finally 中释放。
        val wifi = context.getSystemService(WifiManager::class.java) ?: return null
        return wifi.createMulticastLock("TensorIMS:adb-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private var sharedDriver: AndroidAdbDiscoveryDriver? = null

        // 在主线程共用一个 resolver，跨实例、跨配对/连接轮次也不会并发 resolve。
        private fun driver(context: Context): AndroidAdbDiscoveryDriver = sharedDriver
            ?: AndroidAdbDiscoveryDriver(context.getSystemService(NsdManager::class.java), handler)
                .also { sharedDriver = it }

        private fun isLocalAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress) return true
            return runCatching {
                val addresses = NetworkInterface.getNetworkInterfaces()?.asSequence()
                    ?.flatMap { it.inetAddresses.asSequence() }?.toList().orEmpty()
                AdbLocalAddress.belongsToDevice(address, addresses)
            }.getOrDefault(false)
        }
    }
}

@Suppress("DEPRECATION")
private class AndroidAdbDiscoveryDriver(
    private val nsd: NsdManager,
    private val handler: Handler,
) : AdbDiscoveryDriver {
    private val resolver = AdbResolveQueue<AdbDiscoveredService>(::startResolve)

    override fun later(delayMillis: Long, action: () -> Unit): DiscoveryCancellation {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return DiscoveryCancellation { handler.removeCallbacks(runnable) }
    }

    override fun discover(kind: AdbServiceKind, listener: AdbDiscoveryDriver.Listener): DiscoveryCancellation {
        val discovery = Discovery(listener)
        nsd.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, discovery)
        return DiscoveryCancellation { discovery.cancel() }
    }

    private inner class Discovery(private val target: AdbDiscoveryDriver.Listener) : NsdManager.DiscoveryListener {
        private var registered = false
        private var cancelled = false
        private var stopping = false
        private var stopAttempts = 0

        fun cancel() {
            cancelled = true
            stop()
        }

        private fun stop() {
            if (!registered || stopping) return
            stopping = true
            stopAttempts++
            try {
                nsd.stopServiceDiscovery(this)
            } catch (_: IllegalArgumentException) {
                // listener 已被系统移除，无需重复停止。
                registered = false
                stopping = false
            } catch (_: Exception) { stopFailed() }
        }

        private fun stopFailed() {
            stopping = false
            if (registered && stopAttempts < 3) handler.postDelayed({ stop() }, 200)
            else Log.w("AdbServiceDiscovery", "Unable to stop NSD discovery")
        }

        override fun onDiscoveryStarted(serviceType: String) { handler.post {
            registered = true
            // 取消可能早于系统的 started 回调；迟到注册仍须立即回收。
            if (cancelled) stop()
        } }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { handler.post {
            registered = false
            if (!cancelled) target.failed(nsdFailure("discovery start", errorCode))
        } }

        override fun onDiscoveryStopped(serviceType: String) { handler.post {
            registered = false
            stopping = false
            if (!cancelled) target.failed(AdbDiscoveryException("NSD discovery stopped"))
        } }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { handler.post { stopFailed() } }
        override fun onServiceFound(serviceInfo: NsdServiceInfo) { handler.post {
            if (!cancelled) target.found(serviceInfo.snapshot())
        } }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) { handler.post {
            if (!cancelled) target.lost(serviceInfo.snapshot())
        } }
    }

    override fun resolve(service: AdbDiscoveredService, result: (Result<AdbDiscoveredService>) -> Unit) =
        resolver.enqueue(service, result)

    @SuppressLint("NewApi") // stopServiceResolution 也通过 T 扩展 7 提供，下面统一按 SDK/扩展版本判断。
    private fun startResolve(service: AdbDiscoveredService, result: (Result<AdbDiscoveredService>) -> Unit): DiscoveryCancellation {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { handler.post {
                result(Result.failure(nsdFailure("resolution", errorCode)))
            } }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) { handler.post {
                result(runCatching { serviceInfo.snapshot() })
            } }
            override fun onResolutionStopped(serviceInfo: NsdServiceInfo) { handler.post {
                result(Result.failure(AdbDiscoveryException("NSD resolution cancelled")))
            } }
            override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { handler.post {
                // 仅明确已结束才能释放串行槽；内部停止失败时仍等待原解析终态。
                if (errorCode == NsdManager.FAILURE_OPERATION_NOT_RUNNING) {
                    result(Result.failure(AdbDiscoveryException("NSD resolution already stopped")))
                }
            } }
        }
        nsd.resolveService(service.platformInfo as NsdServiceInfo, listener)
        return DiscoveryCancellation {
            if (AdbNsdCompatibility.supportsResolutionCancellation(Build.VERSION.SDK_INT,
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU))) {
                try { nsd.stopServiceResolution(listener) }
                catch (_: IllegalArgumentException) {
                    // 系统 listener 已移除；排队的旧结果仍由 request 身份过滤。
                    result(Result.failure(AdbDiscoveryException("NSD resolution already removed")))
                }
                catch (_: Exception) { Log.w("AdbServiceDiscovery", "Unable to cancel NSD resolution") }
            }
            // 未更新到 T 扩展 7 的 API 33 无取消 API：上层失效后仍等旧终态回调释放槽。
        }
    }

    private fun NsdServiceInfo.snapshot() = AdbDiscoveredService(
        name = serviceName.orEmpty(),
        type = serviceType.orEmpty(),
        network = network,
        port = port,
        addresses = if (Build.VERSION.SDK_INT >= 34) hostAddresses else listOfNotNull(host),
        platformInfo = this,
    )

    private fun nsdFailure(stage: String, code: Int): AdbDiscoveryException =
        if (code == NsdManager.FAILURE_PERMISSION_DENIED) AdbDiscoveryPermissionException()
        else AdbDiscoveryException("NSD $stage failed ($code)")
}
