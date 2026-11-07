package it.stagepilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.stagepilot.app.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun GigDossierScreen(gig: Gig, store: Store, close: () -> Unit) {
    var editing by remember(gig.id) { mutableStateOf(false) }
    var draft by remember(gig.id) { mutableStateOf(gig) }
    var baseline by remember(gig.id) { mutableStateOf(gig) }
    var confirmClose by remember { mutableStateOf(false) }
    fun back() { if(editing && draft != baseline) confirmClose = true else close() }
    BackHandler { back() }
    val shown = if(editing) draft else gig
    Column(Modifier.fillMaxSize().background(Palco.Pitch).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            GhostButton("‹ Concerti") { back() }
            Spacer(Modifier.weight(1f))
            GhostButton(if(editing) "Salva dossier" else "Modifica dossier") {
                if(editing) { if(store.saveEventDraft(baseline,draft)) editing=false }
                else { baseline=gig;draft=gig;editing=true }
            }
        }
        Text(gig.title,color=Palco.Ink,fontFamily=Sans,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text("${gig.date} · ${gig.location} · Dossier offline",color=Palco.Amber,fontFamily=Sans,fontSize=14.sp)
        DossierHeading("La giornata")
        DossierValue("Indirizzo",shown.address,editing) {draft=draft.copy(address=it)}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            DossierValue("Arrivo e scarico",shown.arrival,editing) {draft=draft.copy(arrival=it)}
            DossierValue("Soundcheck",shown.soundcheck,editing) {draft=draft.copy(soundcheck=it)}
            DossierValue("Apertura porte",shown.doors,editing) {draft=draft.copy(doors=it)}
            DossierValue("Inizio",shown.time,editing) {draft=draft.copy(time=it)}
            DossierValue("Fine prevista",shown.end,editing) {draft=draft.copy(end=it)}
        }
        DossierHeading("Contatti utili")
        if(shown.contacts.isEmpty()) DossierHint("Aggiungi il fonico e il referente del locale dal web o da Modifica dossier.")
        shown.contacts.forEach { c ->
            if(editing) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PalcoField(c.name,{v->draft=draft.copy(contacts=draft.contacts.map {if(it.id==c.id)it.copy(name=v)else it})},"Nome contatto")
                PalcoField(c.role,{v->draft=draft.copy(contacts=draft.contacts.map {if(it.id==c.id)it.copy(role=v)else it})},"Ruolo")
                PalcoField(c.phone,{v->draft=draft.copy(contacts=draft.contacts.map {if(it.id==c.id)it.copy(phone=v)else it})},"Telefono")
                GhostButton("Rimuovi contatto",danger=true){draft=draft.copy(contacts=draft.contacts.filterNot{it.id==c.id})}
            } else DossierHint("${c.name} · ${c.role}\n${c.phone}")
        }
        if(editing) GhostButton("+ Aggiungi contatto") {draft=draft.copy(contacts=draft.contacts+GigContact(store.newId("contact")))}
        DossierHeading("Da portare e da fare")
        DossierHint("${shown.checklist.count{it.done}} di ${shown.checklist.size} completati")
        shown.checklist.forEach { c ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(c.done,{done->
                    if(editing) draft=draft.copy(checklist=draft.checklist.map {if(it.id==c.id)it.copy(done=done)else it})
                    else store.updateBand {b->b.copy(events=b.events.map {g->if(g.id==gig.id)g.copy(checklist=g.checklist.map {if(it.id==c.id)it.copy(done=done)else it})else g})}
                })
                Column(Modifier.weight(1f)) {
                    if(editing) {
                        PalcoField(c.text,{v->draft=draft.copy(checklist=draft.checklist.map {if(it.id==c.id)it.copy(text=v)else it})},"Attività / attrezzatura")
                        PalcoField(c.owner,{v->draft=draft.copy(checklist=draft.checklist.map {if(it.id==c.id)it.copy(owner=v)else it})},"Responsabile")
                        GhostButton("Rimuovi attività",danger=true){draft=draft.copy(checklist=draft.checklist.filterNot{it.id==c.id})}
                    } else DossierHint("${c.text}\n${c.owner}")
                }
            }
        }
        if(editing) GhostButton("+ Aggiungi attività") {draft=draft.copy(checklist=draft.checklist+GigCheck(store.newId("check")))}
        DossierHeading("Disposizione sul palco")
        DossierHint("Vista dal pubblico. Fondo palco in alto.")
        BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)).background(Palco.Panel)) {
            val w=maxWidth
            Text("FONDO PALCO",color=Palco.Dim,fontFamily=Sans,fontSize=10.sp,modifier=Modifier.align(Alignment.TopCenter).padding(8.dp))
            shown.stagePlot.forEach {p->
                Column(Modifier.offset(x=(w-112.dp)*(p.x.coerceIn(0.0,100.0)/100).toFloat(),y=24.dp+200.dp*(p.y.coerceIn(0.0,100.0)/100).toFloat()).width(112.dp).clip(RoundedCornerShape(10.dp)).background(Palco.AmberDeep).padding(8.dp)) {
                    Text(p.label.ifBlank{"Musicista"},color=Palco.Ink,fontFamily=Sans,fontSize=13.sp,fontWeight=FontWeight.Bold)
                    Text(p.instrument,color=Palco.Amber,fontFamily=Sans,fontSize=12.sp)
                }
            }
            Text("PUBBLICO",color=Palco.Dim,fontFamily=Sans,fontSize=10.sp,modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp))
        }
        if(editing) {
            draft.stagePlot.forEach {p->
                PalcoField(p.label,{v->draft=draft.copy(stagePlot=draft.stagePlot.map{if(it.id==p.id)it.copy(label=v)else it})},"Nome sul palco")
                PalcoField(p.instrument,{v->draft=draft.copy(stagePlot=draft.stagePlot.map{if(it.id==p.id)it.copy(instrument=v)else it})},"Strumento")
                Choice("Posizione orizzontale",listOf("20" to "Sinistra","50" to "Centro","80" to "Destra"),p.x.toInt().toString()){v->draft=draft.copy(stagePlot=draft.stagePlot.map{if(it.id==p.id)it.copy(x=v.toDouble())else it})}
                Choice("Profondità",listOf("20" to "Fondo","50" to "Centro","80" to "Fronte"),p.y.toInt().toString()){v->draft=draft.copy(stagePlot=draft.stagePlot.map{if(it.id==p.id)it.copy(y=v.toDouble())else it})}
                GhostButton("Rimuovi musicista",danger=true){draft=draft.copy(stagePlot=draft.stagePlot.filterNot{it.id==p.id})}
            }
            GhostButton("+ Aggiungi musicista") {draft=draft.copy(stagePlot=draft.stagePlot+StagePosition(store.newId("position")))}
        }
        DossierHeading("Ingressi del mixer")
        if(shown.inputs.isEmpty()) DossierHint("Prepara la lista da consegnare al fonico.")
        shown.inputs.forEach {c->
            if(editing) {
                PalcoField(c.channel,{v->draft=draft.copy(inputs=draft.inputs.map{if(it.id==c.id)it.copy(channel=v)else it})},"Canale")
                PalcoField(c.label,{v->draft=draft.copy(inputs=draft.inputs.map{if(it.id==c.id)it.copy(label=v)else it})},"Sorgente")
                PalcoField(c.mic,{v->draft=draft.copy(inputs=draft.inputs.map{if(it.id==c.id)it.copy(mic=v)else it})},"Microfono / DI")
                PalcoField(c.notes,{v->draft=draft.copy(inputs=draft.inputs.map{if(it.id==c.id)it.copy(notes=v)else it})},"Note tecniche")
                GhostButton("Rimuovi ingresso",danger=true){draft=draft.copy(inputs=draft.inputs.filterNot{it.id==c.id})}
            } else DossierHint("${c.channel} · ${c.label} · ${c.mic}\n${c.notes}")
        }
        if(editing) GhostButton("+ Aggiungi ingresso") {draft=draft.copy(inputs=draft.inputs+GigInput(store.newId("input"),channel=(draft.inputs.size+1).toString()))}
        Spacer(Modifier.height(20.dp))
    }
    if(confirmClose) PalcoDialog("Bozza non salvata", "Esci senza salvare", {close()}, {confirmClose=false}) {DossierHint("Puoi tornare al dossier per completare e salvare le modifiche.")}
}
@Composable private fun DossierHeading(text:String) {Text(text,color=Palco.Ink,fontFamily=Sans,fontSize=21.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=14.dp))}
@Composable private fun DossierHint(text:String) {Text(text,color=Palco.Dim,fontFamily=Sans,fontSize=15.sp)}
@Composable private fun DossierValue(label:String,value:String,editing:Boolean,change:(String)->Unit) {
    if(editing) PalcoField(value,change,label,modifier=Modifier.widthIn(max=360.dp)) else Column(Modifier.padding(end=18.dp,bottom=8.dp)){DossierHint(label);Text(value.ifBlank{"Da definire"},color=Palco.Ink,fontFamily=Sans,fontSize=17.sp)}
}
