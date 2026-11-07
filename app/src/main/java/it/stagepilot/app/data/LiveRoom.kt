package it.stagepilot.app.data

import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

@Serializable data class LiveCue(val session: String, val sequence: Long = 0, val index: Int = 0, val playing: Boolean = false, val skipLead: Boolean = false, val anchor: String = "", val action: Long = 0)
@Serializable data class LiveLibrary(val session: String, val db: DB, val protocol:Int = 1)

/** Immutable set snapshot: live commands never write to either library. */
class LiveRoom(val library: LiveLibrary, val code: String = (100000 + SecureRandom().nextInt(900000)).toString(), private val now: () -> Long = System::currentTimeMillis) {
    private var cue = LiveCue(library.session)
    private val peers = mutableMapOf<String,Long>()
    @Synchronized fun current() = cue
    @Synchronized fun publish(index:Int,playing:Boolean,skipLead:Boolean=false,anchor:String="",action:Long=0) {
        require(index in library.db.band.curSet!!.items.indices)
        val next=LiveCue(library.session,cue.sequence,index,playing,skipLead,anchor,action)
        if(next!=cue) cue=next.copy(sequence=cue.sequence+1)
    }
    fun authorized(value:String?):Boolean = value != null && value.length==6 && MessageDigest.isEqual(code.toByteArray(),value.toByteArray())
    @Synchronized fun touch(id:String) { if(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) {peers[id]=now(); peers.entries.removeAll{now()-it.value>15000}} }
    @Synchronized fun peerCount():Int = peers.values.count{now()-it<5000}
    companion object {
        fun snapshot(band:Band,set:SetList):LiveLibrary {
            require(set.items.isNotEmpty()) {"La scaletta è vuota."}
            val ids=set.items.mapNotNull{it.id}.toSet()
            val songs=band.songs.filter{it.id in ids}.map{it.copy(tr=0,scroll=1.0,notes=it.notes.toMap(),tags=it.tags.toList())}
            require(set.items.all{it.isMark || songs.any{s->s.id==it.id}}) {"La scaletta contiene un brano mancante."}
            val shared=Band(band.id,band.name,songs=songs,sets=listOf(set.copy(items=set.items.toList())),cur=set.id)
            return LiveLibrary(UUID.randomUUID().toString(),DB(bands=listOf(shared),curBand=band.id))
        }
    }
}

class LiveRoomServer(private val room:LiveRoom,port:Int=8766):NanoHTTPD(port) {
    override fun serve(session:IHTTPSession):Response {
        fun reply(status:Response.Status,text:String)=newFixedLengthResponse(status,"application/json; charset=utf-8",text).apply {
            addHeader("Cache-Control","no-store");addHeader("X-Content-Type-Options","nosniff")
        }
        if(session.method!=Method.GET)return reply(Response.Status.METHOD_NOT_ALLOWED,"{\"error\":\"Sola lettura\"}")
        if(!room.authorized(session.headers["x-live-code"]))return reply(Response.Status.FORBIDDEN,"{\"error\":\"Codice non valido\"}")
        if(session.headers["origin"]!=null)return reply(Response.Status.FORBIDDEN,"{\"error\":\"Usa StagePilot\"}")
        return when(session.uri) {
            "/live/library"->reply(Response.Status.OK,AppJson.encodeToString(LiveLibrary.serializer(),room.library))
            "/live/state"->{
                if(session.headers["x-live-session"]!=room.library.session)return reply(Response.Status.CONFLICT,"{\"error\":\"Sessione terminata\"}")
                room.touch(session.headers["x-live-device"].orEmpty())
                reply(Response.Status.OK,AppJson.encodeToString(LiveCue.serializer(),room.current()))
            }
            else->reply(Response.Status.NOT_FOUND,"{\"error\":\"Non trovato\"}")
        }
    }
}
