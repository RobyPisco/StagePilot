package it.stagepilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.data.*

@Composable fun BandLiveScreen(db:DB,controller:BandLiveController,state:BandLiveState,onStage:()->Unit,close:()->Unit) {
    var setId by remember {mutableStateOf(db.band.curSet?.id.orEmpty())}
    var address by rememberSaveable {mutableStateOf("")}
    var code by rememberSaveable {mutableStateOf("")}
    BackHandler(onBack=close)
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        GhostButton("‹ Oggi",onClick=close)
        Text("Band Live",fontFamily=Sans,fontSize=32.sp,fontWeight=FontWeight.Bold,color=Palco.Ink)
        Text("Un tablet guida. La band segue. Funziona sulla stessa Wi-Fi, anche senza PC e Internet.",fontFamily=Sans,fontSize=16.sp,color=Palco.Dim)
        if(state.message.isNotBlank()) Text(state.message,color=Palco.Amber,fontFamily=Sans,fontSize=15.sp)
        if(state.busy) Text("Collegamento in corso…",color=Palco.Dim,fontFamily=Sans)
        else if(state.role.isNotBlank()) {
            Text(if(state.role=="leader")"SEI IL CAPOFILA" else "SEGUI IL CAPOFILA",color=Palco.Amber,fontFamily=Sans,fontSize=13.sp)
            Text(state.library?.db?.band?.curSet?.name.orEmpty(),fontFamily=Sans,fontSize=25.sp,color=Palco.Ink)
            if(state.role=="leader") {
                Text("Indirizzo: ${state.address}\nCodice: ${state.code}",fontFamily=Mono,fontSize=21.sp,color=Palco.Ink)
                Text("${state.peers} tablet collegati. Gli altri entrano con questi dati. La scaletta resta fissata per questa sessione.",fontFamily=Sans,fontSize=15.sp,color=Palco.Dim)
            }
            AmberButton(if(state.role=="leader")"Apri il palco e guida" else "Apri il palco") {onStage()}
            GhostButton("Termina / esci dalla sessione",danger=true){controller.stop()}
        } else if(!state.busy) {
            Text("Guida tu la band",fontFamily=Sans,fontSize=22.sp,color=Palco.Ink,fontWeight=FontWeight.Bold)
            Choice("Scaletta da condividere",db.band.sets.map{it.id to it.name},setId){setId=it}
            AmberButton("Apri sessione come capofila",enabled=db.band.sets.any{it.id==setId&&it.items.isNotEmpty()}) {db.band.sets.firstOrNull{it.id==setId}?.let{controller.host(db.band,it)}}
            Text("Oppure segui un altro tablet",fontFamily=Sans,fontSize=22.sp,color=Palco.Ink,fontWeight=FontWeight.Bold)
            PalcoField(address,{address=it},"Indirizzo del capofila",supporting="es. 192.168.1.20:8766")
            PalcoField(code,{code=it.filter(Char::isDigit).take(6)},"Codice della sessione",keyboard=KeyboardType.Number)
            AmberButton("Entra nella band",enabled=address.isNotBlank()&&code.length==6){controller.join(address,code,db)}
            GhostButton("Apri l’ultima sessione offline"){controller.reopenCache()}
        }
    }
}
