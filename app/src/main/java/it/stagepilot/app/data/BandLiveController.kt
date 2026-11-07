package it.stagepilot.app.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URL
import java.util.UUID
private class LiveSessionEnded:Exception("Sessione terminata. Rientra con il nuovo codice del capofila.")

data class BandLiveState(val role:String="",val library:LiveLibrary?=null,val cue:LiveCue?=null,val address:String="",val code:String="",val connected:Boolean=false,val following:Boolean=true,val peers:Int=0,val message:String="",val busy:Boolean=false)

class BandLiveController(context:Context) {
    private val state=MutableStateFlow(BandLiveState())
    val status=state.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private var server:LiveRoomServer?=null
    private var room:LiveRoom?=null
    @Volatile private var generation=0
    private val cache=File(context.filesDir,"band-live-cache.json")
    private val device=UUID.randomUUID().toString()

    fun host(band:Band,set:SetList) {
        stop();val epoch=generation;state.value=BandLiveState(busy=true)
        job=scope.launch {
            try {
                val live=LiveRoom(LiveRoom.snapshot(band,set));val http=LiveRoomServer(live)
                http.start(3000,false)
                if(epoch!=generation){http.stop();return@launch}
                room=live;server=http
                val addresses=NetworkInterface.getNetworkInterfaces().toList().sortedBy{if(it.name.startsWith("wlan"))0 else 1}.flatMap{it.inetAddresses.toList()}.filterIsInstance<Inet4Address>().filter{!it.isLoopbackAddress&&it.isSiteLocalAddress}
                state.value=BandLiveState("leader",live.library,live.current(),(addresses.firstOrNull()?.hostAddress?:"127.0.0.1")+":8766",live.code,true,message="Sessione aperta sulla Wi-Fi. Il PC non serve.")
                while(isActive){delay(1000);state.update{it.copy(peers=live.peerCount())}}
            } catch(e:CancellationException){throw e}
              catch(e:Exception){if(epoch==generation)state.value=BandLiveState(message="Non riesco ad aprire la sessione: ${e.message}")}
        }
    }
    fun join(address:String,code:String,local:DB) {
        stop();val epoch=generation;state.value=BandLiveState(busy=true)
        job=scope.launch {
            try {
                val base=liveBaseUrl(address)
                require(code.matches(Regex("[0-9]{6}"))){"Il codice deve avere 6 cifre."}
                val downloaded=AppJson.decodeFromString(LiveLibrary.serializer(),get(base,"/live/library",code))
                require(downloaded.protocol==1&&downloaded.session.isNotBlank()&&downloaded.db.bands.size==1){"Sessione non valida o versione incompatibile."}
                val band=downloaded.db.band
                val set=band.curSet?:error("Scaletta mancante.")
                require(set.items.isNotEmpty()&&set.items.all{it.isMark||band.song(it.id)!=null}){"Scaletta incompleta: chiedi al capofila di riaprirla."}
                val own=downloaded.copy(db=downloaded.db.copy(opt=local.opt,bands=listOf(band.copy(me=local.band.me,songs=band.songs.map{s->local.band.song(s.id)?.let{s.copy(tr=it.tr,scroll=it.scroll)}?:s}))))
                if(epoch!=generation)return@launch
                val temp=File(cache.parentFile,cache.name+".tmp");temp.writeText(AppJson.encodeToString(LiveLibrary.serializer(),own));check(temp.renameTo(cache)){"Non riesco a conservare la copia offline."}
                if(epoch!=generation)return@launch
                state.value=BandLiveState("follower",own,address=base.removePrefix("http://"),connected=true,message="Collegato. La libreria personale resta separata.")
                while(isActive) {
                    try {
                        val cue=AppJson.decodeFromString(LiveCue.serializer(),get(base,"/live/state",code,own.session))
                        require(cue.session==own.session&&cue.index in set.items.indices){"La sessione del capofila è cambiata."}
                        if(epoch!=generation)return@launch
                        state.update{it.copy(cue=cue,connected=true,message="Collegato al capofila")}
                    } catch(e:LiveSessionEnded){if(epoch==generation)state.update{it.copy(connected=false,following=false,message=e.message.orEmpty())};break
                    } catch(e:CancellationException){throw e}
                      catch(e:Exception){if(epoch==generation)state.update{it.copy(connected=false,message="Collegamento perso. Il chart resta disponibile; riprovo…")}}
                    delay(if(state.value.connected)350 else 1500)
                }
            } catch(e:CancellationException){throw e}
              catch(e:Exception){if(epoch==generation)state.value=BandLiveState(message=e.message?:"Collegamento non riuscito.")}
        }
    }
    fun reopenCache() {
        stop()
        runCatching{AppJson.decodeFromString(LiveLibrary.serializer(),cache.readText())}.onSuccess {
            state.value=BandLiveState("follower",it,following=false,message="Copia offline dell’ultima sessione. Lettura libera.")
        }.onFailure{state.value=BandLiveState(message="Non c’è ancora una sessione salvata sul tablet.")}
    }
    fun follow(value:Boolean){state.update{it.copy(following=value)}}
    fun publish(index:Int,playing:Boolean,skipLead:Boolean,anchor:String,action:Long){room?.publish(index,playing,skipLead,anchor,action)}
    fun updateSong(id:String,change:(Song)->Song){state.update{s->s.copy(library=s.library?.let{lib->lib.copy(db=lib.db.copy(bands=lib.db.bands.map{b->b.copy(songs=b.songs.map{if(it.id==id)change(it)else it})}))})}}
    fun stop(){generation++;job?.cancel();job=null;server?.stop();server=null;room=null;state.value=BandLiveState()}
    fun close(){stop();scope.cancel()}
    private fun get(base:String,path:String,code:String,session:String?=null):String {
        val c=URL(base+path).openConnection() as HttpURLConnection
        c.connectTimeout=1800;c.readTimeout=2200;c.setRequestProperty("X-Live-Code",code)
        if(session!=null){c.setRequestProperty("X-Live-Session",session);c.setRequestProperty("X-Live-Device",device)}
        try {
            when(c.responseCode){403->error("Codice errato. Controlla il codice sul tablet capofila.");409->throw LiveSessionEnded();200->Unit;else->error("Il tablet non risponde come StagePilot.")}
            val bytes=c.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);require(out.size()<=5_000_000){"Scaletta troppo grande per questa sessione."}}
                out.toByteArray()
            }
            require(bytes.size<=5_000_000){"Scaletta troppo grande per questa sessione."}
            return bytes.toString(Charsets.UTF_8)
        } finally {c.disconnect()}
    }
    companion object {
        fun liveBaseUrl(address:String):String {
            val raw=address.trim().removePrefix("http://").trimEnd('/')
            val uri=URI("http://"+if(':' in raw)raw else "$raw:8766")
            require(uri.host!=null&&uri.userInfo==null&&uri.rawQuery==null&&uri.rawFragment==null&&uri.path.isNullOrEmpty()&&uri.port in 1..65535){"Usa l’indirizzo del capofila, es. 192.168.1.20:8766"}
            return uri.toString()
        }
    }
}
