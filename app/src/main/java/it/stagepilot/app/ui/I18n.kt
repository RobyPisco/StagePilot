package it.stagepilot.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

val SUPPORTED_LANGS = listOf("auto", "it", "en", "es", "fr", "pt")

/** Risolve la lingua effettiva ("it", "en", "es", "fr", "pt") a partire dalla
 *  preferenza salvata e dalla lingua di sistema. */
fun resolveLang(pref: String?, systemLang: String? = Locale.getDefault().language): String {
    val p = (pref ?: "auto").lowercase(Locale.ROOT)
    if (p in listOf("it", "en", "es", "fr", "pt")) return p
    val sys = (systemLang ?: "en").lowercase(Locale.ROOT).take(2)
    return if (sys in listOf("it", "en", "es", "fr", "pt")) sys else "en"
}

/** Stringhe reattive dell'interfaccia in 5 lingue (IT, EN, ES, FR, PT).
 *  I ruoli salvati nel DB restano in italiano come chiavi; a schermo si traducono. */
object Str {
    var code by mutableStateOf("it"); private set

    fun apply(pref: String?) {
        code = resolveLang(pref)
    }

    private fun pick(it: String, en: String, es: String, fr: String, pt: String): String = when (code) {
        "it" -> it
        "es" -> es
        "fr" -> fr
        "pt" -> pt
        else -> en
    }

    // Tabs
    val tabHome get() = pick("Oggi", "Today", "Hoy", "Aujourd'hui", "Hoje")
    val tabSongs get() = pick("Brani", "Songs", "Canciones", "Chansons", "Músicas")
    val tabSets get() = pick("Scalette", "Setlists", "Listas", "Setlists", "Setlists")
    val tabPractice get() = pick("Prove", "Practice", "Ensayos", "Répéts", "Ensaios")
    val tabConcerts get() = pick("Concerti", "Gigs", "Conciertos", "Concerts", "Shows")
    val tabOptions get() = pick("Opzioni", "Settings", "Ajustes", "Réglages", "Opções")

    // Home
    val bandLiveBtn get() = pick("Band Live · collega i tablet", "Band Live · link tablets", "Band Live · conectar tablets", "Band Live · relier tablettes", "Band Live · ligar tablets")
    val guideBtn get() = pick("Guida e aiuto", "Guide & help", "Guía y ayuda", "Guide et aide", "Guia e ajuda")
    val syncBusy get() = pick("Sincronizzazione in corso…", "Syncing…", "Sincronizando…", "Synchronisation…", "Sincronizando…")
    val syncNow get() = pick("Sincronizza con il PC", "Sync with PC", "Sincronizar con PC", "Synchroniser avec le PC", "Sincronizar com o PC")
    val connectPc get() = pick("Collega lo studio PC", "Connect PC studio", "Conectar estudio PC", "Relier le studio PC", "Ligar estúdio PC")
    val syncHint get() = pick("Dopo le modifiche sul web, tocca qui per aggiornare brani e scalette.", "After web edits, tap here to update songs and setlists.", "Tras editar en la web, toca aquí para actualizar.", "Après des modifications web, touchez ici pour mettre à jour.", "Após editar na web, toque aqui para atualizar.")
    val homeTitle get() = pick("La tua musica.\nIl tuo prossimo palco.", "Your music.\nYour next stage.", "Tu música.\nTu próximo escenario.", "Votre musique.\nVotre prochaine scène.", "Sua música.\nSeu próximo palco.")
    val homeSub get() = pick("Tutto pronto, un brano alla volta.", "Everything ready, one song at a time.", "Todo listo, una canción a la vez.", "Tout est prêt, un morceau à la fois.", "Tudo pronto, uma música de cada vez.")
    val nextGigLabel get() = pick("LA TUA PROSSIMA SERATA", "YOUR NEXT GIG", "TU PRÓXIMO CONCIERTO", "VOTRE PROCHAIN CONCERT", "SEU PRÓXIMO SHOW")
    val goStage get() = pick("▶  Vai sul palco", "▶  Go on stage", "▶  Ir al escenario", "▶  Monter sur scène", "▶  Ir para o palco")
    val statSongs get() = pick("brani in repertorio", "songs in library", "canciones", "morceaux", "músicas")
    val statReady get() = pick("pronti da suonare", "ready to play", "listas para tocar", "prêts à jouer", "prontas")
    val statSets get() = pick("scalette", "setlists", "listas", "setlists", "setlists")
    val nextPractice get() = pick("La prossima prova", "Next rehearsal", "Próximo ensayo", "Prochaine répétition", "Próximo ensaio")
    val importSong get() = pick("Importa un brano", "Import song", "Importar canción", "Importer un morceau", "Importar música")
    val prepGig get() = pick("Prepara una serata", "Plan a gig", "Preparar concierto", "Préparer un concert", "Preparar um show")

    // Settings
    val optionsTitle get() = pick("Opzioni", "Settings", "Ajustes", "Réglages", "Opções")
    val optionsSub get() = pick("Gruppo, scalette, lettura, guida e backup", "Band, setlists, reading, guide & backup", "Banda, listas, lectura, guía y respaldo", "Groupe, setlists, lecture, guide et sauvegarde", "Banda, setlists, leitura, guia e backup")
    val guideCardTitle get() = pick("Guida interna e aiuto", "Built-in guide & help", "Guía interna y ayuda", "Guide intégré et aide", "Guia interno e ajuda")
    val guideCardSub get() = pick("Spiega tutte le funzioni: palco, pedale, PDF, Band Live, dossier e backup.", "Explains all features: stage, pedal, PDF, Band Live, dossier & backup.", "Explica todas las funciones: escenario, pedal, PDF, Band Live y respaldo.", "Explique toutes les fonctions : scène, pédale, PDF, Band Live et sauvegarde.", "Explica todas as funções: palco, pedal, PDF, Band Live e backup.")
    val secBand get() = pick("Gruppo", "Band", "Banda", "Groupe", "Banda")
    val secSets get() = pick("Scalette", "Setlists", "Listas", "Setlists", "Setlists")
    val secReading get() = pick("Come si legge", "Reading & stage", "Lectura y escenario", "Lecture et scène", "Leitura e palco")
    val secBackup get() = pick("Backup", "Backup", "Respaldo", "Sauvegarde", "Backup")
    val secStudio get() = pick("Studio web", "Web studio", "Estudio web", "Studio web", "Estúdio web")
    val langLabel get() = pick("Lingua dell'interfaccia", "App language", "Idioma de la app", "Langue de l'application", "Idioma do app")
    val langAuto get() = pick("Auto", "Auto", "Auto", "Auto", "Auto")
    val themeHint get() = pick("Tema dell'app: Palco al buio, Giorno ad alto contrasto per esterni", "App theme: Stage for dark venues, Day high-contrast for outdoors", "Tema: Escenario para oscuridad, Día alto contraste para exterior", "Thème : Scène pour le noir, Jour contraste élevé en extérieur", "Tema: Palco no escuro, Dia alto contraste para externos")
    val themeDark get() = pick("Palco (scuro)", "Stage (dark)", "Escenario (oscuro)", "Scène (sombre)", "Palco (escuro)")
    val themeLight get() = pick("Giorno (chiaro)", "Day (light)", "Día (claro)", "Jour (clair)", "Dia (claro)")
    val notationHint get() = pick("Notazione degli accordi", "Chord notation", "Notación de acordes", "Notation des accords", "Notação de acordes")
    val accidHint get() = pick("Alterazioni: automatiche scelgono dalla tonalità (Si♭ in Fa, La# in Mi)", "Accidentals: automatic picks from key (B♭ in F, C# in D)", "Alteraciones: automáticas según la tonalidad", "Altérations : automatiques selon la tonalité", "Alterações: automáticas conforme a tonalidade")
    val accidAuto get() = pick("Automatiche", "Automatic", "Automáticas", "Automatiques", "Automáticas")
    val accidSharp get() = pick("Diesis", "Sharps", "Sostenidos", "Dièses", "Sustenidos")
    val accidFlat get() = pick("Bemolli", "Flats", "Bemoles", "Bémols", "Bemóis")
    val liveTextSize get() = pick("Corpo del testo in live", "Live text size", "Tamaño en vivo", "Taille en scène", "Tamanho ao vivo")
    val togBar get() = pick("Barra dei sei comandi", "Six-button bar", "Barra de seis botones", "Barre à six boutons", "Barra de seis botões")
    val togTimer get() = pick("Cronometro del set", "Set timer", "Cronómetro del set", "Chrono du set", "Cronômetro do set")
    val togPulse get() = pick("Lampeggio dei BPM", "BPM visual pulse", "Pulso visual BPM", "Clignotement BPM", "Pulso visual BPM")
    val togClick get() = pick("Click del metronomo", "Metronome click", "Clic del metrónomo", "Clic du métronome", "Clique do metrônomo")
    val togCaps get() = pick("Testo in maiuscolo", "Uppercase lyrics", "Letra en mayúsculas", "Paroles en majuscules", "Letra em maiúsculas")
    val togRoots get() = pick("Solo le note fondamentali", "Roots only", "Solo fundamentales", "Fondamentales seules", "Apenas fundamentais")
    val togPlain get() = pick("Solo testo", "Lyrics only", "Solo letra", "Paroles seules", "Apenas letra")
    val pedalHint get() = pick("Pedale Bluetooth / tastiera (frecce destra/sinistra e PagSu/PagGiù)", "Bluetooth pedal / keyboard (left/right arrows & PageUp/PageDown)", "Pedal Bluetooth / teclado (flechas izq/der y RePág/AvPág)", "Pédale Bluetooth / clavier (flèches gauche/droite et PageHaut/Bas)", "Pedal Bluetooth / teclado (setas esq/dir e PageUp/PageDown)")
    val pedalSong get() = pick("Brano prec / succ", "Prev / next song", "Canción ant / sig", "Morceau préc / suiv", "Música ant / próx")
    val pedalPage get() = pick("Scorri pagina", "Scroll half page", "Desplazar página", "Défiler demi-page", "Rolar meia página")
    val exportBtn get() = pick("Esporta", "Export", "Exportar", "Exporter", "Exportar")
    val importBtn get() = pick("Importa", "Import", "Importar", "Importer", "Importar")
    val undoBtn get() = pick("Annulla ultima modifica", "Undo last change", "Deshacer último cambio", "Annuler la dernière modif", "Desfazer última alteração")

    // Editor
    val newSong get() = pick("Nuovo brano", "New song", "Nueva canción", "Nouveau morceau", "Nova música")
    val editSong get() = pick("Modifica brano", "Edit song", "Editar canción", "Modifier le morceau", "Editar música")
    val saveUpper get() = pick("SALVA", "SAVE", "GUARDAR", "SAUVER", "SALVAR")
    val editMode get() = pick("Modifica", "Edit", "Editar", "Modifier", "Editar")
    val previewMode get() = pick("Anteprima", "Preview", "Vista previa", "Aperçu", "Prévia")
    val discardTitle get() = pick("Modifiche non salvate", "Unsaved changes", "Cambios sin guardar", "Modifications non sauvées", "Alterações não salvas")
    val discardBtn get() = pick("Esci senza salvare", "Discard", "Salir sin guardar", "Quitter sans sauver", "Sair sem salvar")
    val discardMsg get() = pick("Ci sono modifiche aperte su questo brano. Se esci adesso andranno perse.", "This song has unsaved changes. Leaving now will discard them.", "Hay cambios sin guardar en esta canción. Si sales ahora se perderán.", "Ce morceau a des modifications non sauvées. Elles seront perdues.", "Há alterações não salvas nesta música. Se sair agora serão perdidas.")

    // Share dialog
    val shareTitle get() = pick("Condividi la scaletta", "Share setlist", "Compartir lista", "Partager la setlist", "Compartilhar setlist")
    val sharePdf get() = pick("PDF da palco (A4)", "Stage PDF (A4)", "PDF de escenario (A4)", "PDF de scène (A4)", "PDF de palco (A4)")
    val shareText get() = pick("Solo elenco (testo)", "Text list only", "Solo lista (texto)", "Liste texte seule", "Apenas lista (texto)")

    // Roles (DB keys stay Italian, UI translates cleanly)
    fun role(r: String): String = when (r) {
        "Tutti" -> pick("Tutti", "Everyone", "Todos", "Tous", "Todos")
        "Voce" -> pick("Voce", "Vocals", "Voz", "Chant", "Voz")
        "Chitarra" -> pick("Chitarra", "Guitar", "Guitarra", "Guitare", "Guitarra")
        "Basso" -> pick("Basso", "Bass", "Bajo", "Basse", "Baixo")
        "Tastiere" -> pick("Tastiere", "Keys", "Teclados", "Claviers", "Teclados")
        "Batteria" -> pick("Batteria", "Drums", "Batería", "Batterie", "Bateria")
        "Fiati" -> pick("Fiati", "Horns", "Vientos", "Cuivres", "Metais")
        else -> r
    }
}
