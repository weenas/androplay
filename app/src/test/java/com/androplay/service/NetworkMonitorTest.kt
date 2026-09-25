package com.androplay.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class NetworkMonitorTest {
    @Test
    fun ssidIsUnquotedAndWithheldValuesAreNull() {
        assertEquals("Home WiFi", NetworkMonitor.cleanSsid("\"Home WiFi\""))
        assertEquals("家里的网络", NetworkMonitor.cleanSsid("\"家里的网络\""))
        assertEquals("rawbytes", NetworkMonitor.cleanSsid("rawbytes"))
        assertNull(NetworkMonitor.cleanSsid("<unknown ssid>"))
        assertNull(NetworkMonitor.cleanSsid(""))
        assertNull(NetworkMonitor.cleanSsid(null))
        assertNull(NetworkMonitor.cleanSsid("\"\""))
    }

    @Test
    fun onlyReachableIpv4AddressesAreShown() {
        val addresses = listOf(
            "10.2.2.72", "127.0.0.1", "169.254.10.20", "fe80::1", "2408:8207::1", "10.2.2.72", "192.168.1.5"
        ).map(InetAddress::getByName)
        assertEquals(listOf("10.2.2.72", "192.168.1.5"), NetworkMonitor.ipv4Addresses(addresses))
    }
}
