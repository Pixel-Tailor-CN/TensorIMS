package app.mystery0.ims.tensor.embedded.adb

import java.io.IOException
import java.net.InetAddress
import java.util.Locale

internal enum class AdbServiceKind(val serviceType: String) {
    PAIRING("_adb-tls-pairing._tcp"),
    CONNECT("_adb-tls-connect._tcp");

    fun matches(type: String) = type.trimEnd('.').lowercase(Locale.ROOT) == serviceType
}

internal open class AdbDiscoveryException(message: String, cause: Throwable? = null) : IOException(message, cause)
internal class AdbDiscoveryAmbiguousException : AdbDiscoveryException("Multiple local ADB services")
internal class AdbDiscoveryPermissionException : AdbDiscoveryException("Local network discovery permission denied")

internal object AdbNsdCompatibility {
    fun supportsResolutionCancellation(sdk: Int, tExtension: Int) = sdk >= 34 || (sdk >= 33 && tExtension >= 7)
}

internal fun interface DiscoveryCancellation { fun cancel() }

internal data class AdbDiscoveredService(
    val name: String,
    val type: String,
    val network: Any? = null,
    val port: Int = 0,
    val addresses: List<InetAddress> = emptyList(),
    val platformInfo: Any? = null,
) {
    val key get() = Key(name, type.trimEnd('.').lowercase(Locale.ROOT), network)
    data class Key(val name: String, val type: String, val network: Any?)
}

internal interface AdbDiscoveryDriver {
    interface Listener {
        fun found(service: AdbDiscoveredService)
        fun lost(service: AdbDiscoveredService)
        fun failed(failure: Exception)
    }

    fun discover(kind: AdbServiceKind, listener: Listener): DiscoveryCancellation
    fun resolve(service: AdbDiscoveredService, result: (Result<AdbDiscoveredService>) -> Unit): DiscoveryCancellation
    fun later(delayMillis: Long, action: () -> Unit): DiscoveryCancellation
}

/** 所有方法均在同一调度线程执行；每轮独立持有候选项，绝不复用上一轮动态端口。 */
internal class AdbDiscoverySession(
    private val kind: AdbServiceKind,
    private val driver: AdbDiscoveryDriver,
    private val isLocal: (InetAddress) -> Boolean,
    private val result: (Result<Int>) -> Unit,
) : AdbDiscoveryDriver.Listener {
    private class Candidate(val service: AdbDiscoveredService) {
        var pending = true
        var attempts = 0
        var port: Int? = null
        var work: DiscoveryCancellation? = null
    }

    private val candidates = linkedMapOf<AdbDiscoveredService.Key, Candidate>()
    private var active = true
    private var started = false
    private var discovery: DiscoveryCancellation? = null
    private var settle: DiscoveryCancellation? = null

    fun start() {
        if (!active || started) return
        started = true
        try {
            val handle = driver.discover(kind, this)
            if (active) discovery = handle else handle.cancel()
        } catch (failure: Exception) { failed(failure) }
    }

    override fun found(service: AdbDiscoveredService) {
        if (!active || !kind.matches(service.type) || service.name.isBlank()) return
        if (service.key in candidates) return
        // 不让同网段的大量无关广播无限占用内存和解析队列。
        if (candidates.size >= 32) {
            failed(AdbDiscoveryException("Too many ADB discovery candidates"))
            return
        }
        settle?.cancel()
        settle = null
        val candidate = Candidate(service)
        candidates[service.key] = candidate
        resolve(candidate)
    }

    override fun lost(service: AdbDiscoveredService) {
        if (!active) return
        candidates.remove(service.key)?.work?.cancel()
        scheduleResult()
    }

    private fun resolve(candidate: Candidate) {
        if (!current(candidate)) return
        candidate.attempts++
        var completedInline = false
        val handle = driver.resolve(candidate.service) resolved@{ resolved ->
            completedInline = true
            // lost 后同名服务重新出现会创建新对象；旧解析不能污染新一轮候选项。
            if (!current(candidate)) return@resolved
            candidate.work = null
            val service = resolved.getOrNull()
            val failure = resolved.exceptionOrNull()
            if (failure is SecurityException || failure is AdbDiscoveryPermissionException) {
                failed(failure)
                return@resolved
            }
            if (service == null && candidate.attempts < 3) {
                candidate.work = driver.later(250L * candidate.attempts) { resolve(candidate) }
                return@resolved
            }
            candidate.pending = false
            candidate.port = service?.takeIf {
                it.name == candidate.service.name && kind.matches(it.type) &&
                    LaunchInput.validPort(it.port) && it.addresses.any(isLocal)
            }?.port
            scheduleResult()
        }
        if (!current(candidate)) handle.cancel()
        else if (!completedInline) candidate.work = handle
    }

    private fun current(candidate: Candidate) = active && candidates[candidate.service.key] === candidate

    private fun scheduleResult() {
        settle?.cancel()
        settle = null
        if (!active || candidates.values.any { it.pending }) return
        if (candidates.values.none { it.port != null }) return
        // 等同批发现回调到齐后再选择，多个本机端口时明确失败而不是误配对。
        settle = driver.later(350) {
            settle = null
            if (!active || candidates.values.any { it.pending }) return@later
            val ports = candidates.values.mapNotNull { it.port }.distinct()
            when {
                ports.size > 1 -> failed(AdbDiscoveryAmbiguousException())
                ports.size == 1 -> finish(Result.success(ports.single()))
            }
        }
    }

    override fun failed(failure: Exception) {
        if (active) finish(Result.failure(failure))
    }

    private fun finish(value: Result<Int>) {
        cancel()
        result(value)
    }

    fun cancel() {
        if (!active) return
        active = false
        settle?.cancel()
        settle = null
        candidates.values.forEach { it.work?.cancel() }
        candidates.clear()
        discovery?.cancel()
        discovery = null
    }
}

/** mDNS 地址只用于确认广播属于本机；实际 Socket 始终使用 LoopbackConnection。 */
internal object AdbLocalAddress {
    fun belongsToDevice(address: InetAddress, localAddresses: Iterable<InetAddress>): Boolean {
        if (address.isAnyLocalAddress || address.isMulticastAddress) return false
        if (address.isLoopbackAddress) return true
        // 比较原始地址字节，不受 IPv6 文本压缩形式和 scope 后缀影响，也不触发 DNS。
        return localAddresses.any { address.address.contentEquals(it.address) }
    }
}
