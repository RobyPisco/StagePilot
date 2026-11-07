package it.stagepilot.app.data

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class LiveRoomTest {
    private fun library():LiveLibrary {
        val band=Band("band","Example",songs=listOf(Song("one",title="Uno",body="# Verse\n[Am]Test"),Song("two",title="Due",body="[C]Test"),Song("other",title="Fuori")),sets=listOf(SetList("set","Live",listOf(SetItem("song","one"),SetItem("mark",label="Pausa"),SetItem("song","two")))),cur="set",events=listOf(Gig("gig","Privato")))
        return LiveRoom.snapshot(band,band.curSet!!)
    }
    @Test fun snapshotOnlyContainsSelectedSetAndNeverTheDossier() {
        val lib=library()
        assertEquals(listOf("one","two"),lib.db.band.songs.map{it.id})
        assertTrue(lib.db.band.events.isEmpty())
        assertEquals(3,lib.db.band.curSet!!.items.size)
        assertNotEquals(lib.session,library().session)
    }
    @Test fun commandsAreOrderedAndIdenticalPollsCannotRestartPlayback() {
        val room=LiveRoom(library(),"123456")
        room.publish(0,true);val seq=room.current().sequence
        room.publish(0,true);assertEquals(seq,room.current().sequence)
        room.publish(2,false,anchor="Chorus",action=1)
        assertTrue(room.current().sequence>seq)
        assertEquals(2,room.current().index)
        assertEquals("Chorus",room.current().anchor)
        assertThrows(IllegalArgumentException::class.java){room.publish(99,true)}
        assertEquals(2,room.current().index)
    }
    @Test fun staleGuestsExpireAndInvalidCodesCannotReadTheRoom() {
        var now=10000L;val room=LiveRoom(library(),"123456"){now}
        assertFalse(room.authorized(null));assertFalse(room.authorized("123457"));assertTrue(room.authorized("123456"))
        room.touch("guest_1");room.touch("../../wrong");assertEquals(1,room.peerCount())
        now+=6000;assertEquals(0,room.peerCount())
    }
    @Test fun httpProtocolProtectsSnapshotAndRejectsStaleSessionAndWrites() {
        val room=LiveRoom(library(),"123456");val server=LiveRoomServer(room,0);server.start(2000,false)
        fun call(path:String,code:String?=null,session:String?=null,method:String="GET"):Pair<Int,String> {
            val c=URL("http://127.0.0.1:${server.listeningPort}"+path).openConnection() as HttpURLConnection
            c.connectTimeout=2000;c.readTimeout=2000;c.requestMethod=method
            code?.let{c.setRequestProperty("X-Live-Code",it)};session?.let{c.setRequestProperty("X-Live-Session",it)}
            c.setRequestProperty("X-Live-Device","test-client")
            return try {val status=c.responseCode;status to (if(status<400)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}}finally{c.disconnect()}
        }
        try {
            assertEquals(403,call("/live/library").first)
            val response=call("/live/library","123456");assertEquals(200,response.first)
            assertEquals(3,AppJson.decodeFromString(LiveLibrary.serializer(),response.second).db.band.curSet!!.items.size)
            assertEquals(409,call("/live/state","123456","old-session").first)
            room.publish(2,true,skipLead=true)
            val state=call("/live/state","123456",room.library.session)
            val cue=AppJson.decodeFromString(LiveCue.serializer(),state.second)
            assertEquals(2,cue.index);assertTrue(cue.playing);assertTrue(cue.skipLead);assertEquals(1,room.peerCount())
            assertEquals(405,call("/live/state","123456",room.library.session,"POST").first)
            assertEquals(404,call("/../files","123456").first)
        }finally{server.stop()}
    }
    @Test fun joiningOnlyAcceptsAnAddressAndNeverCredentialsOrPaths() {
        assertEquals("http://192.168.1.20:8766",BandLiveController.liveBaseUrl("192.168.1.20"))
        for(value in listOf("http://user:pass@192.168.1.20:8766","http://192.168.1.20:8766/private","https://example.com","127.0.0.1:8766?x=1"))
            assertThrows(Exception::class.java){BandLiveController.liveBaseUrl(value)}
    }
}
