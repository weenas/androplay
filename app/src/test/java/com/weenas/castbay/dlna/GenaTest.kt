package com.weenas.castbay.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenaTest {
    @Test
    fun readsCallbacksAndTimeouts() {
        assertEquals(
            listOf("http://10.0.0.5:4000/cb", "http://10.0.0.5:4001/"),
            Gena.callbacks("<http://10.0.0.5:4000/cb><http://10.0.0.5:4001/>")
        )
        assertEquals(emptyList<String>(), Gena.callbacks(null))
        assertEquals(300, Gena.timeoutSeconds("Second-300"))
        assertEquals(1800, Gena.timeoutSeconds("Second-infinite"))
        assertEquals(1800, Gena.timeoutSeconds(null))
        assertEquals(60, Gena.timeoutSeconds("Second-5"))
    }

    @Test
    fun wrapsStateInAnEscapedLastChange() {
        val body = Gena.propertySet(
            UpnpDescriptions.AV_TRANSPORT,
            listOf("TransportState" to "PLAYING", "AVTransportURI" to "http://cdn/v.mp4?a=1&b=2")
        )
        assertTrue(body.startsWith("<?xml"))
        assertTrue("&lt;TransportState val=&quot;PLAYING&quot;/&gt;" in body)
        // The URL's "&" is escaped once as an attribute value, then again inside LastChange.
        assertTrue("a=1&amp;amp;b=2" in body)
        val volume = Gena.propertySet(UpnpDescriptions.RENDERING_CONTROL, listOf("Volume" to "40"))
        assertTrue("metadata-1-0/RCS/" in volume)
        assertTrue("channel=&quot;Master&quot;" in volume)
    }
}
