package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class AdbDiscoverySessionTest {
    private val local = address("192.168.1.4")
    private val foreign = address("192.168.1.9")
    private val loopback = address("127.0.0.1")

    @Test fun pairingAndConnectTypesRemainDistinct() {
        assertTrue(AdbServiceKind.PAIRING.matches("_adb-tls-pairing._tcp."))
        assertTrue(AdbServiceKind.CONNECT.matches("_ADB-TLS-CONNECT._TCP"))
        assertFalse(AdbServiceKind.PAIRING.matches(AdbServiceKind.CONNECT.serviceType))
        assertFalse(AdbServiceKind.CONNECT.matches("_adb._tcp"))
        assertFalse(AdbServiceKind.CONNECT.matches("_adb-tls-connect._tcp.evil"))
    }

    @Test fun cancellationApiRespectsAndroidAndExtensionBoundary() {
        assertFalse(AdbNsdCompatibility.supportsResolutionCancellation(32, 7))
        assertFalse(AdbNsdCompatibility.supportsResolutionCancellation(33, 0))
        assertFalse(AdbNsdCompatibility.supportsResolutionCancellation(33, 6))
        assertTrue(AdbNsdCompatibility.supportsResolutionCancellation(33, 7))
        assertTrue(AdbNsdCompatibility.supportsResolutionCancellation(33, 8))
        assertTrue(AdbNsdCompatibility.supportsResolutionCancellation(34, 0))
        assertTrue(AdbNsdCompatibility.supportsResolutionCancellation(37, 0))
    }

    @Test fun androidResolvedTypeWithLeadingDotIsAcceptedForBothKinds() {
        for (kind in AdbServiceKind.entries) {
            val fixture = Fixture(kind)
            val discovered = service("device", kind).copy(type = kind.serviceType + ".")
            fixture.driver.found(discovered)
            fixture.driver.complete(0, discovered.copy(
                type = "." + kind.serviceType, port = 30001, addresses = listOf(local)))
            fixture.driver.advance(350)
            assertEquals(30001, fixture.results.single().getOrThrow())
        }
    }

    @Test fun lostWithEquivalentTypeCancelsResolveAndRejectsLateResult() {
        val fixture = Fixture()
        val discovered = service("device").copy(type = AdbServiceKind.CONNECT.serviceType + ".")
        fixture.driver.found(discovered)
        fixture.driver.lost(discovered.copy(type = "." + AdbServiceKind.CONNECT.serviceType))
        fixture.driver.complete(0, discovered.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertTrue(fixture.driver.resolutions.single().cancelled)
        assertTrue(fixture.results.isEmpty())
    }

    @Test fun addressPolicyAcceptsOnlyDeviceAndLoopbackAddresses() {
        assertTrue(AdbLocalAddress.belongsToDevice(local, listOf(local)))
        assertTrue(AdbLocalAddress.belongsToDevice(loopback, emptyList()))
        assertTrue(AdbLocalAddress.belongsToDevice(address("::1"), emptyList()))
        assertTrue(AdbLocalAddress.belongsToDevice(address("fe80::1234"), listOf(address("fe80:0:0:0:0:0:0:1234"))))
        assertFalse(AdbLocalAddress.belongsToDevice(foreign, listOf(local)))
        assertFalse(AdbLocalAddress.belongsToDevice(address("0.0.0.0"), listOf(address("0.0.0.0"))))
        assertFalse(AdbLocalAddress.belongsToDevice(address("224.0.0.251"), listOf(address("224.0.0.251"))))
        assertFalse(AdbLocalAddress.belongsToDevice(address("::"), emptyList()))
    }

    @Test fun foreignBroadcastIsNeverSelected() {
        val fixture = Fixture()
        val remote = service("remote")
        val device = service("device")
        fixture.driver.found(remote)
        fixture.driver.found(device)
        fixture.driver.complete(0, remote.copy(port = 30001, addresses = listOf(foreign)))
        fixture.driver.complete(1, device.copy(port = 30002, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertEquals(30002, fixture.results.single().getOrThrow())
        assertEquals(1, fixture.driver.stopCount)
    }

    @Test fun wrongServiceTypeIsIgnoredBeforeResolve() {
        val fixture = Fixture()
        fixture.driver.found(service("pairing").copy(type = AdbServiceKind.PAIRING.serviceType))
        assertTrue(fixture.driver.resolutions.isEmpty())
        assertTrue(fixture.results.isEmpty())
    }

    @Test fun lostAndRediscoveredServiceIgnoresOldResolve() {
        val fixture = Fixture()
        val device = service("device")
        fixture.driver.found(device)
        fixture.driver.lost(device)
        fixture.driver.found(device)
        fixture.driver.complete(0, device.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertTrue(fixture.results.isEmpty())
        fixture.driver.complete(1, device.copy(port = 30002, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertEquals(30002, fixture.results.single().getOrThrow())
        assertTrue(fixture.driver.resolutions[0].cancelled)
    }

    @Test fun lostDuringSettleDoesNotReturnStalePort() {
        val fixture = Fixture()
        val device = service("device")
        fixture.driver.found(device)
        fixture.driver.complete(0, device.copy(port = 30001, addresses = listOf(loopback)))
        fixture.driver.lost(device)
        fixture.driver.advance(1000)
        assertTrue(fixture.results.isEmpty())
    }

    @Test fun cancelDiscardsCallbacksAndStopsDiscoveryExactlyOnce() {
        val fixture = Fixture()
        val device = service("device")
        fixture.driver.found(device)
        fixture.session.cancel()
        fixture.session.cancel()
        fixture.driver.complete(0, device.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.found(service("another"))
        fixture.driver.listener.failed(AdbDiscoveryException("late"))
        fixture.driver.advance(1000)
        assertTrue(fixture.results.isEmpty())
        assertEquals(1, fixture.driver.stopCount)
        assertEquals(1, fixture.driver.resolutions.size)
        assertTrue(fixture.driver.resolutions.single().cancelled)
    }

    @Test fun multipleLocalPortsReportAmbiguity() {
        val fixture = Fixture(AdbServiceKind.PAIRING)
        val first = service("code", AdbServiceKind.PAIRING)
        val second = service("qr", AdbServiceKind.PAIRING)
        fixture.driver.found(first)
        fixture.driver.found(second)
        fixture.driver.complete(0, first.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.complete(1, second.copy(port = 30002, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertTrue(fixture.results.single().exceptionOrNull() is AdbDiscoveryAmbiguousException)
    }

    @Test fun samePortOnTwoNetworksIsNotAmbiguous() {
        val fixture = Fixture()
        val first = service("device").copy(network = "wifi")
        val second = first.copy(network = "ethernet")
        fixture.driver.found(first)
        fixture.driver.found(second)
        fixture.driver.complete(0, first.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.complete(1, second.copy(port = 30001, addresses = listOf(loopback)))
        fixture.driver.advance(350)
        assertEquals(30001, fixture.results.single().getOrThrow())
    }

    @Test fun waitsForAllKnownResolutionsBeforeSelecting() {
        val fixture = Fixture()
        val first = service("first")
        val second = service("second")
        fixture.driver.found(first)
        fixture.driver.found(second)
        fixture.driver.complete(0, first.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.advance(1000)
        assertTrue(fixture.results.isEmpty())
        fixture.driver.complete(1, second.copy(port = 30002, addresses = listOf(foreign)))
        fixture.driver.advance(350)
        assertEquals(30001, fixture.results.single().getOrThrow())
    }

    @Test fun repeatedFoundDoesNotStartConcurrentDuplicateResolve() {
        val fixture = Fixture()
        repeat(10) { fixture.driver.found(service("device")) }
        assertEquals(1, fixture.driver.resolutions.size)
    }

    @Test fun retriesTransientResolveFailureWithBoundedBackoff() {
        val fixture = Fixture()
        val device = service("device")
        fixture.driver.found(device)
        fixture.driver.fail(0)
        fixture.driver.advance(249)
        assertEquals(1, fixture.driver.resolutions.size)
        fixture.driver.advance(1)
        fixture.driver.fail(1)
        fixture.driver.advance(500)
        fixture.driver.complete(2, device.copy(port = 30001, addresses = listOf(local)))
        fixture.driver.advance(350)
        assertEquals(30001, fixture.results.single().getOrThrow())
    }

    @Test fun retriesStopAfterThreeFailuresAndCancelClearsBackoff() {
        val fixture = Fixture()
        fixture.driver.found(service("device"))
        fixture.driver.fail(0)
        fixture.driver.advance(250)
        fixture.driver.fail(1)
        fixture.driver.advance(500)
        fixture.driver.fail(2)
        fixture.driver.advance(2000)
        assertEquals(3, fixture.driver.resolutions.size)
        fixture.session.cancel()
        assertTrue(fixture.results.isEmpty())

        val cancelled = Fixture()
        cancelled.driver.found(service("device"))
        cancelled.driver.fail(0)
        cancelled.session.cancel()
        cancelled.driver.advance(500)
        assertEquals(1, cancelled.driver.resolutions.size)
    }

    @Test fun invalidPortsMismatchedNameAndTypeAreNotAccepted() {
        val device = service("device")
        val valid = device.copy(port = 30001, addresses = listOf(local))
        for (invalid in listOf(valid.copy(port = 0), valid.copy(port = 65536), valid.copy(name = "other"),
            valid.copy(type = AdbServiceKind.PAIRING.serviceType), valid.copy(addresses = emptyList()))) {
            val fixture = Fixture()
            fixture.driver.found(device)
            fixture.driver.complete(0, invalid)
            fixture.driver.advance(1000)
            assertTrue(fixture.results.isEmpty())
            fixture.session.cancel()
        }
    }

    @Test fun discoveryFailureCleansCandidatesAndIsReportedOnce() {
        val fixture = Fixture()
        fixture.driver.found(service("device"))
        val failure = AdbDiscoveryException("failed")
        fixture.driver.listener.failed(failure)
        fixture.driver.listener.failed(failure)
        assertSame(failure, fixture.results.single().exceptionOrNull())
        assertTrue(fixture.driver.resolutions.single().cancelled)
        assertEquals(1, fixture.driver.stopCount)
    }

    @Test fun permissionFailureStopsImmediatelyWithoutRetry() {
        val fixture = Fixture()
        fixture.driver.found(service("device"))
        fixture.driver.resolutions[0].result(Result.failure(AdbDiscoveryPermissionException()))
        fixture.driver.advance(1000)
        assertTrue(fixture.results.single().exceptionOrNull() is AdbDiscoveryPermissionException)
        assertEquals(1, fixture.driver.resolutions.size)
        assertEquals(1, fixture.driver.stopCount)
    }

    @Test fun startIsIdempotentAndCancelledSessionCannotRestart() {
        val fixture = Fixture()
        fixture.session.start()
        assertEquals(1, fixture.driver.startCount)
        fixture.session.cancel()
        fixture.session.start()
        assertEquals(1, fixture.driver.startCount)
    }

    @Test fun nextSessionMustResolveDynamicPortAgain() {
        val first = Fixture()
        val device = service("device")
        first.driver.found(device)
        first.driver.complete(0, device.copy(port = 30001, addresses = listOf(local)))
        first.driver.advance(350)
        val next = Fixture()
        next.driver.advance(1000)
        assertTrue(next.results.isEmpty())
        next.driver.found(device)
        next.driver.complete(0, device.copy(port = 30002, addresses = listOf(local)))
        next.driver.advance(350)
        assertEquals(30002, next.results.single().getOrThrow())
    }

    private inner class Fixture(kind: AdbServiceKind = AdbServiceKind.CONNECT) {
        val driver = Driver()
        val results = mutableListOf<Result<Int>>()
        val session = AdbDiscoverySession(kind, driver, { AdbLocalAddress.belongsToDevice(it, listOf(local)) }, results::add)
            .also { it.start() }
    }

    private class Driver : AdbDiscoveryDriver {
        data class Resolution(val result: (Result<AdbDiscoveredService>) -> Unit, var cancelled: Boolean = false)
        data class Scheduled(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        lateinit var listener: AdbDiscoveryDriver.Listener
        val resolutions = mutableListOf<Resolution>()
        private val scheduled = mutableListOf<Scheduled>()
        private var now = 0L
        var stopCount = 0
        var startCount = 0
        override fun discover(kind: AdbServiceKind, listener: AdbDiscoveryDriver.Listener): DiscoveryCancellation {
            startCount++
            this.listener = listener
            return DiscoveryCancellation { stopCount++ }
        }
        override fun resolve(service: AdbDiscoveredService, result: (Result<AdbDiscoveredService>) -> Unit): DiscoveryCancellation {
            val resolution = Resolution(result).also(resolutions::add)
            return DiscoveryCancellation { resolution.cancelled = true }
        }
        override fun later(delayMillis: Long, action: () -> Unit): DiscoveryCancellation {
            val work = Scheduled(now + delayMillis, action).also(scheduled::add)
            return DiscoveryCancellation { work.cancelled = true }
        }
        fun found(service: AdbDiscoveredService) = listener.found(service)
        fun lost(service: AdbDiscoveredService) = listener.lost(service)
        fun complete(index: Int, service: AdbDiscoveredService) = resolutions[index].result(Result.success(service))
        fun fail(index: Int) = resolutions[index].result(Result.failure(AdbDiscoveryException("test")))
        fun advance(millis: Long) {
            val end = now + millis
            while (true) {
                val work = scheduled.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                scheduled.remove(work)
                now = work.at
                work.action()
            }
            now = end
        }
    }

    private fun service(name: String, kind: AdbServiceKind = AdbServiceKind.CONNECT) = AdbDiscoveredService(name, kind.serviceType)
    private fun address(text: String): InetAddress = InetAddress.getByName(text)
}
