package com.weenas.castbay.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpTest {
    private val uuid = "5f2c7a1e-0000-4000-8000-000000000001"

    @Test
    fun parsesSearchesAndIgnoresEverythingElse() {
        val search = Ssdp.parseSearch(
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        )
        assertEquals(Ssdp.Search(Ssdp.MEDIA_RENDERER, 3), search)
        // Header names and the MAN value are matched case-insensitively; a missing MX means 1.
        assertEquals(Ssdp.Search("ssdp:all", 1), Ssdp.parseSearch("M-SEARCH * HTTP/1.1\nman: ssdp:discover\nst: ssdp:all\n\n"))
        assertNull(Ssdp.parseSearch("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n"))
        assertNull(Ssdp.parseSearch("M-SEARCH * HTTP/1.1\r\nST: ssdp:all\r\n\r\n"))
    }

    @Test
    fun answersOnlyMatchingTargets() {
        assertEquals(6, Ssdp.matches(Ssdp.Search("ssdp:all", 1), uuid).size)
        assertEquals(
            listOf(Ssdp.AV_TRANSPORT to "uuid:$uuid::${Ssdp.AV_TRANSPORT}"),
            Ssdp.matches(Ssdp.Search(Ssdp.AV_TRANSPORT, 1), uuid)
        )
        assertEquals(listOf("uuid:$uuid" to "uuid:$uuid"), Ssdp.matches(Ssdp.Search("uuid:$uuid", 1), uuid))
        assertTrue(Ssdp.matches(Ssdp.Search("urn:dial-multiscreen-org:service:dial:1", 1), uuid).isEmpty())
    }

    @Test
    fun buildsResponsesWithLocation() {
        val target = Ssdp.targets(uuid).first()
        val response = Ssdp.searchResponse(target, "http://10.0.0.2:4000/description.xml")
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue("LOCATION: http://10.0.0.2:4000/description.xml\r\n" in response)
        assertTrue("USN: uuid:$uuid::upnp:rootdevice\r\n" in response)
        assertTrue(response.endsWith("\r\n\r\n"))
        assertTrue("NTS: ssdp:byebye\r\n" in Ssdp.byeBye(target))
    }
}
