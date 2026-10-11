package app.mystery0.ims.tensor.embedded.adb

import org.junit.Assert.*
import org.junit.Test

class ComputerAdbCommandTest {
    private val path = "/data/app/~~abc/app.mystery0.ims.tensor-123/lib/arm64/libtensorims_starter.so"

    @Test fun createsOnlyTheInstalledStarterPathAndAndroidUser() {
        assertEquals("adb shell $path --user 10", ComputerAdbCommand.create(path, 10))
    }

    @Test fun acceptsInstalledIsaNamesRatherThanApkAbiNames() {
        assertEquals("adb shell $path --user 0", ComputerAdbCommand.create(path, 0))
        val emulatorPath = path.replace("/lib/arm64/", "/lib/x86_64/")
        assertEquals("adb shell $emulatorPath --user 0", ComputerAdbCommand.create(emulatorPath, 0))
    }

    @Test fun acceptsOnlyFixedNativeStarterInInstalledIsaDirectory() {
        for (invalid in listOf(
            "", "/sdcard/libtensorims_starter.so", "/data/local/tmp/libtensorims_starter.so",
            "/data/app/pkg/lib/arm64-v8a/other.so", "/data/app/pkg/lib/armeabi-v7a/libtensorims_starter.so",
            "/data/app/pkg/lib/arm64-v8a/libtensorims_starter.so",
            "/data/app/../other/lib/arm64-v8a/libtensorims_starter.so",
            "/data/app/pkg/lib/arm64-v8a/libtensorims_starter.so;id",
        )) {
            assertThrows(IllegalArgumentException::class.java) { ComputerAdbCommand.create(invalid, 0) }
        }
    }

    @Test fun rejectsUserIdsThatCannotBeUsedInAndroidUidCalculation() {
        for (invalid in listOf(-1, Int.MAX_VALUE, Int.MAX_VALUE / 100000 + 1)) {
            assertThrows(IllegalArgumentException::class.java) { ComputerAdbCommand.create(path, invalid) }
        }
        assertTrue(ComputerAdbCommand.create(path, Int.MAX_VALUE / 100000).endsWith("--user ${Int.MAX_VALUE / 100000}"))
    }
}
