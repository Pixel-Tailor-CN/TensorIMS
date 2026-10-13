package app.mystery0.ims.tensor.embedded

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EmbeddedAutoStartPermitTest {
    @Test fun revokedOrExpiredDeliveryCannotPublishServer() {
        var allowed = true
        var published = false
        val permit = EmbeddedAutoStartPermit("request", 3, { allowed }, { true })
        allowed = false
        assertFalse(permit.commitDelivery { published = true; true })
        allowed = true
        permit.revoke()
        assertFalse(permit.commitDelivery { published = true; true })
        assertFalse(published)
    }

    @Test fun revocationCannotCompleteWhileAnEarlierDeliveryIsBeingCommitted() {
        val permit = EmbeddedAutoStartPermit("request", 3, { true }, { true })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val revoking = CountDownLatch(1)
        val revoked = CountDownLatch(1)
        val delivery = Thread { permit.commitDelivery { entered.countDown(); release.await(); true } }
        val cancellation = Thread { revoking.countDown(); permit.revoke(); revoked.countDown() }
        delivery.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            cancellation.start()
            assertTrue(revoking.await(2, TimeUnit.SECONDS))
            assertFalse(revoked.await(100, TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            delivery.join(2_000)
            cancellation.join(2_000)
        }
        assertTrue(revoked.await(2, TimeUnit.SECONDS))
        assertFalse(permit.canContinue())
    }

    @Test fun revokedPrecheckCannotDispatchOrAcceptLateDelivery() {
        val permit = EmbeddedAutoStartPermit("request", 3, { true }, { true })
        assertTrue(permit.canContinue())
        assertTrue(permit.revoke())
        assertFalse(permit.canContinue())
        assertFalse(permit.tryBeginBootstrap())
    }

    @Test fun backgroundDiscoveryCannotEnterBootstrap() {
        var foreground = true
        val permit = EmbeddedAutoStartPermit("request", 3, { true }, { foreground })
        foreground = false
        assertFalse(permit.canContinue())
        assertFalse(permit.tryBeginBootstrap())
    }

    @Test fun activityGapCannotBeginBootstrapButCanKeepBoundedDiscovery() {
        val permit = EmbeddedAutoStartPermit("request", 3, { true }, { true }, { false })
        assertTrue(permit.canContinue())
        assertFalse(permit.tryBeginBootstrap())
    }

    @Test fun admittedBootstrapSurvivesBackgroundButNotAuthorityRevocation() {
        var foreground = true
        var allowed = true
        val permit = EmbeddedAutoStartPermit("request", 3, { allowed }, { foreground })
        assertTrue(permit.tryBeginBootstrap())
        foreground = false
        assertFalse(permit.revoke(beforeBootstrapOnly = true))
        assertTrue(permit.canContinue())
        allowed = false
        assertFalse(permit.canContinue())
    }
}
