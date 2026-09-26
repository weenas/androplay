package com.androplay.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DlnaRendererTest {
    private class FakeTarget : DlnaRenderer.Target {
        val calls = mutableListOf<String>()
        var status = DlnaRenderer.Status(DlnaState.STOPPED)
        override fun open(url: String, title: String?) { calls += "open $url $title" }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun stop() { calls += "stop" }
        override fun seek(positionSec: Double) { calls += "seek $positionSec" }
        override fun setVolume(percent: Int) { calls += "volume $percent" }
        override fun setMuted(muted: Boolean) { calls += "mute $muted" }
        override fun status() = status
    }

    private val target = FakeTarget()
    private val renderer = DlnaRenderer(target)

    private fun soap(action: String, args: String = "") = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
<u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"><InstanceID>0</InstanceID>$args</u:$action>
</s:Body></s:Envelope>"""

    private fun call(action: String, args: String = "", service: UpnpDescriptions.Service = UpnpDescriptions.AV_TRANSPORT) =
        renderer.handle(service, Soap.parse(soap(action, args), null)!!).toMap()

    @Test
    fun parsesActionsWithEscapedMetadata() {
        val action = Soap.parse(
            soap("SetAVTransportURI", "<CurrentURI>http://cdn/v.mp4?a=1&amp;b=2</CurrentURI>" +
                "<CurrentURIMetaData>&lt;DIDL-Lite&gt;&lt;item&gt;&lt;dc:title&gt;晴天 &amp;amp; MV&lt;/dc:title&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>"),
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\""
        )!!
        assertEquals("SetAVTransportURI", action.name)
        assertEquals("http://cdn/v.mp4?a=1&b=2", action.args["CurrentURI"])
        assertEquals("晴天 & MV", DlnaState.title(action.args["CurrentURIMetaData"]))
    }

    @Test
    fun fallsBackToTheSoapActionHeader() {
        assertEquals("Play", Soap.parse("not xml", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"")?.name)
        assertNull(Soap.parse("not xml", null))
    }

    @Test
    fun drivesTheTargetAndReportsItsState() {
        assertEquals(DlnaState.NO_MEDIA, call("GetTransportInfo")["CurrentTransportState"])
        call("SetAVTransportURI", "<CurrentURI>http://cdn/v.mp4</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>")
        call("Play", "<Speed>1</Speed>")
        call("Seek", "<Unit>REL_TIME</Unit><Target>00:01:30</Target>")
        call("Pause")
        call("SetVolume", "<Channel>Master</Channel><DesiredVolume>35</DesiredVolume>", UpnpDescriptions.RENDERING_CONTROL)
        assertEquals(listOf("open http://cdn/v.mp4 null", "play", "seek 90.0", "pause", "volume 35"), target.calls)

        target.status = DlnaRenderer.Status(DlnaState.PAUSED, positionSec = 3725.4, durationSec = 5400.0)
        assertEquals(DlnaState.PAUSED, call("GetTransportInfo")["CurrentTransportState"])
        val position = call("GetPositionInfo")
        assertEquals("1:02:05", position["RelTime"])
        assertEquals("1:30:00", position["TrackDuration"])
        assertEquals("http://cdn/v.mp4", position["TrackURI"])
    }

    @Test
    fun refusesWhatItCantDo() {
        for ((action, code) in listOf("Play" to 701, "Next" to 701, "Record" to 401)) {
            try {
                call(action)
                fail("$action should fail")
            } catch (fault: Soap.Fault) {
                assertEquals(code, fault.code)
            }
        }
        val response = Soap.fault(Soap.Fault(701, "Transition not available"))
        assertTrue("<errorCode>701</errorCode>" in response)
    }

    @Test
    fun parsesAndFormatsTimes() {
        assertEquals(90.5, DlnaState.parseTime("0:01:30.500")!!, 0.001)
        assertEquals(42.0, DlnaState.parseTime("42")!!, 0.001)
        assertNull(DlnaState.parseTime("NOT_IMPLEMENTED"))
        assertEquals("0:00:00", DlnaState.formatTime(-3.0))
    }
}
