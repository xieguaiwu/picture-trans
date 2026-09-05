package com.xieguiawu.picturetrans.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test fun `wifi-like interface detection`() {
        assertTrue(NetworkUtils.isWifiLike("wlan0"))
        assertTrue(NetworkUtils.isWifiLike("wlan1"))
        assertTrue(NetworkUtils.isWifiLike("swlan0"))
        assertTrue(NetworkUtils.isWifiLike("ap0"))
        assertFalse(NetworkUtils.isWifiLike("rmnet0"))
        assertFalse(NetworkUtils.isWifiLike("lo"))
        assertFalse(NetworkUtils.isWifiLike("tun0"))
        assertFalse(NetworkUtils.isWifiLike("eth0"))
        assertFalse(NetworkUtils.isWifiLike("dummy0"))
    }

    @Test fun `url format`() {
        assertEquals(
            "http://192.168.1.5:8765/t/abc123/",
            NetworkUtils.url("192.168.1.5", 8765, "abc123"),
        )
    }
}
