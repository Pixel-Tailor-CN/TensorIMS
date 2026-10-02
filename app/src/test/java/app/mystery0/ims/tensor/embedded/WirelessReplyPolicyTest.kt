package app.mystery0.ims.tensor.embedded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessReplyPolicyTest {
    @Test fun acceptsOnlyCurrentSixDigitReply() {
        assertTrue(WirelessReplyPolicy.accepts("current", "current", WirelessAdbPhase.WAITING_CODE, "012345"))
        for (code in listOf(null, "", "12345", "1234567", "abcdef", "１２３４５６", "123 45")) {
            assertFalse(WirelessReplyPolicy.accepts("current", "current", WirelessAdbPhase.WAITING_CODE, code))
        }
    }

    @Test fun rejectsStaleNotificationsAfterCancelOrProcessDeath() {
        assertFalse(WirelessReplyPolicy.accepts(null, "old", WirelessAdbPhase.WAITING_CODE, "123456"))
        assertFalse(WirelessReplyPolicy.accepts("new", "old", WirelessAdbPhase.WAITING_CODE, "123456"))
        assertFalse(WirelessReplyPolicy.accepts(null, null, WirelessAdbPhase.WAITING_CODE, "123456"))
    }

    @Test fun rejectsDuplicateRepliesDuringPairingOrConnection() {
        for (phase in WirelessAdbPhase.entries.filter { it != WirelessAdbPhase.WAITING_CODE }) {
            assertFalse(WirelessReplyPolicy.accepts("current", "current", phase, "123456"))
        }
    }
}
