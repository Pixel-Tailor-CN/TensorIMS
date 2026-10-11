package app.mystery0.ims.tensor.bridge

import org.junit.Assert.*
import org.junit.Test

class ComputerBootstrapIdentityTest {
    private val identity = InstalledIdentity(1012345, 10, 3, "signer", "/data/app/p/base.apk")

    @Test fun digestIsStableAndBindsInstalledPackageAndNativeDirectory() {
        val digest = ComputerBootstrapIdentity.digest(identity, "/data/app/p/lib/arm64-v8a")
        assertEquals(digest, ComputerBootstrapIdentity.digest(identity, "/data/app/p/lib/arm64-v8a"))
        assertNotEquals(digest, ComputerBootstrapIdentity.digest(identity.copy(uid = 1012346), "/data/app/p/lib/arm64-v8a"))
        assertNotEquals(digest, ComputerBootstrapIdentity.digest(identity, "/data/app/q/lib/arm64-v8a"))
        assertTrue(digest.matches(Regex("[0-9a-f]{64}")))
    }
}
