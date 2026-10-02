package app.mystery0.ims.tensor.ui

import android.Manifest
import app.mystery0.ims.tensor.embedded.WirelessAdbPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessAdbUiActionsTest {
    @Test fun `只有本次配对准备完成后才能前台跳设置`() {
        for (phase in WirelessAdbPhase.entries) {
            assertEquals(phase in setOf(WirelessAdbPhase.SEARCHING_PAIRING, WirelessAdbPhase.WAITING_CODE),
                wirelessAdbShouldOpenSettings(waiting = true, active = true, phase))
            assertFalse(wirelessAdbShouldOpenSettings(waiting = false, active = true, phase))
            assertFalse(wirelessAdbShouldOpenSettings(waiting = true, active = false, phase))
        }
    }

    @Test fun `Android 13 到 16 只请求尚未授予的通知权限`() {
        for (sdk in 33..36) {
            assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS),
                wirelessAdbPermissionsToRequest(sdk, notificationsGranted = false, localNetworkGranted = false))
            assertTrue(wirelessAdbPermissionsToRequest(sdk, notificationsGranted = true, localNetworkGranted = false).isEmpty())
        }
    }

    @Test fun `Android 17 发现前补齐通知和本地网络权限`() {
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_LOCAL_NETWORK),
            wirelessAdbPermissionsToRequest(37, notificationsGranted = false, localNetworkGranted = false))
        assertEquals(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
            wirelessAdbPermissionsToRequest(37, notificationsGranted = true, localNetworkGranted = false))
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS),
            wirelessAdbPermissionsToRequest(37, notificationsGranted = false, localNetworkGranted = true))
    }

    @Test fun `已授权重连和后续系统版本不重复请求权限`() {
        assertTrue(wirelessAdbPermissionsToRequest(37, notificationsGranted = true, localNetworkGranted = true).isEmpty())
        assertTrue(wirelessAdbPermissionsToRequest(38, notificationsGranted = true, localNetworkGranted = true).isEmpty())
        assertEquals(listOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
            wirelessAdbPermissionsToRequest(38, notificationsGranted = true, localNetworkGranted = false))
    }
}
