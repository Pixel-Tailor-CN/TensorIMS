package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test

class BootstrapCommandTest {
    @Test fun detachesServerBeforeAdbShellCloses() {
        val command = BootstrapCommand.create(0, 10345, 27, "a".repeat(64), "b".repeat(64), "/data/app/x/base.apk")
        assertTrue(command.contains(" /system/bin/setsid /system/bin/app_process "))
    }
    @Test fun commandQuotesEveryArgumentAndOnlyUsesFixedEntryPoint() {
        val command = BootstrapCommand.create(0, 10345, 27, "a".repeat(64), "b".repeat(64), "/data/app/a'b/base.apk")
        assertTrue(command.startsWith("CLASSPATH='/data/app/a'\\''b/base.apk' /system/bin/setsid /system/bin/app_process /system/bin"))
        assertTrue(command.contains("app.mystery0.ims.tensor.embedded.EmbeddedServerMain '0' '10345' '27'"))
        assertTrue(command.endsWith("'/data/app/a'\\''b/base.apk' </dev/null >/dev/null 2>&1 &"))
    }
    @Test fun invalidIdentityNeverCreatesCommand() {
        for (apk in listOf("base.apk", "/tmp/base.apk", "/data/app/x\n/base.apk", "/data/app/../other.apk")) {
            assertThrows(IllegalArgumentException::class.java) { BootstrapCommand.create(0, 10345, 27, "a".repeat(64), "b".repeat(64), apk) }
        }
        assertThrows(IllegalArgumentException::class.java) { BootstrapCommand.create(10, 10345, 27, "a".repeat(64), "b".repeat(64), "/data/app/x/base.apk") }
        assertThrows(IllegalArgumentException::class.java) { BootstrapCommand.create(0, 10345, 27, "garbage", "b".repeat(64), "/data/app/x/base.apk") }
    }
}
