package app.mystery0.ims.tensor.bridge

import app.mystery0.ims.tensor.BuildConfig
import app.mystery0.ims.tensor.embedded.adb.BootstrapCommand
import app.mystery0.ims.tensor.privilege.OperationType
import org.junit.Assert.*
import org.junit.Test

/** 同一组测试分别在两个 flavor 执行，防止安装身份与代码 namespace 再次混用。 */
class PackageIdentityTest {
    @Test fun bridgeTargetsOnlyCurrentFlavor() {
        val expected = when (BuildConfig.FLAVOR) {
            "tensor" -> "app.mystery0.ims.tensor"
            "legacy" -> "io.github.vvb2060.ims"
            else -> error("Unexpected identity flavor")
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID)
        assertEquals(expected, BridgeProtocol.PACKAGE)
        assertEquals("$expected.embedded.bridge", BridgeProtocol.AUTHORITY)
    }

    @Test fun instrumentationClassesKeepSharedNamespace() {
        OperationType.entries.forEach {
            assertEquals("app.mystery0.ims.tensor.privileged.${it.instrumentationClassName}", it.componentClassName)
        }
    }

    @Test fun bootstrapSeparatesPackageAndUserButKeepsFixedEntry() {
        val apk = "/data/app/${BuildConfig.APPLICATION_ID}-random/base.apk"
        val command = BootstrapCommand.create(10, 1010345, 27, "a".repeat(64), "b".repeat(64), apk)
        assertTrue(command.startsWith("CLASSPATH='$apk' "))
        assertTrue(command.contains("--nice-name='${BuildConfig.APPLICATION_ID}:embedded:10' "))
        assertTrue(command.contains("app.mystery0.ims.tensor.embedded.EmbeddedServerMain '10' '1010345'"))
        assertFalse(command.contains("io.github.vvb2060.ims.embedded.EmbeddedServerMain"))
    }

    @Test fun siblingUidCannotUseSameSignerBridge() {
        val policy = BridgePolicy(10345, 0, "same-release-signer", "challenge", 100, 30000)
        assertTrue(policy.authenticate(10345, 0, "same-release-signer"))
        assertFalse(policy.authenticate(10346, 0, "same-release-signer"))
        assertFalse(policy.authenticate(1010345, 10, "same-release-signer"))
    }
}
