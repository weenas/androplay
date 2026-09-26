package com.weenas.castbay.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class DacpClientTest {
    @Test
    fun serviceNameCarriesTheDacpId() {
        assertEquals("iTunes_Ctrl_503E8EBBE4DD2AAC", DacpClient.serviceNameFor("503E8EBBE4DD2AAC"))
    }

    @Test
    fun requestCarriesCommandAndActiveRemote() {
        val request = DacpClient.buildRequest(DacpClient.Command.PLAY_PAUSE, "1634972906", "10.2.2.5:3689")
        assertEquals(
            "GET /ctrl-int/1/playpause HTTP/1.1\r\n" +
                "Host: 10.2.2.5:3689\r\n" +
                "Active-Remote: 1634972906\r\n" +
                "Connection: close\r\n\r\n",
            request
        )
        assertEquals("nextitem", DacpClient.Command.NEXT.path)
        assertEquals("previtem", DacpClient.Command.PREVIOUS.path)
    }

    @Test
    fun hostHeaderBracketsIpv6AndDropsZone() {
        assertEquals("10.2.2.5:3689", DacpClient.hostHeader(InetAddress.getByName("10.2.2.5"), 3689))
        assertEquals(
            "[fe80:0:0:0:491:7d31:145c:ea65]:3689",
            DacpClient.hostHeader(InetAddress.getByName("fe80::491:7d31:145c:ea65"), 3689)
        )
    }
}
