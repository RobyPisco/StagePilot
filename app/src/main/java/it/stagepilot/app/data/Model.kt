package it.stagepilot.app.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/* Il formato JSON è lo stesso del database v4 dell'app web: un backup
   esportato da là si importa qui senza conversioni. */

/** Ruoli fissi. "Tutti" è la nota di arrangiamento che vede la band. I nomi
 *  sono anche le chiavi delle note salvate sul brano, quindi restano in
 *  italiano in qualunque lingua. */
val ROLES = listOf("Tutti", "Voce", "Chitarra", "Basso", "Tastiere", "Batteria", "Fiati")

/** Nel JSON web il BPM è un numero oppure la stringa vuota. */
object BpmSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor
    override fun deserialize(decoder: Decoder): Int? {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        return (el as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
    }
    override fun serialize(encoder: Encoder, value: Int?) {
        (encoder as JsonEncoder).encodeJsonElement(
            if (value == null) JsonPrimitive("") else JsonPrimitive(value),
        )
    }
}

@Serializable
data class Song(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val key: String = "",
    @Serializable(with = BpmSerializer::class) val bpm: Int? = null,
    val dur: String = "",
    val tags: List<String> = emptyList(),
    val notes: Map<String, String> = emptyMap(),
    /** trasposizione in semitoni, salvata sul brano */
    val tr: Int = 0,
    val capo: Int = 0,
    /** correzione dello scorrimento, 0.5–4, salvata sul brano */
    val scroll: Double = 1.0,
    /** secondi di attesa iniziale prima che il chart parta */
    val lead: Int = 0,
    val body: String = "",
    /** study / rehearse / ready. These are shared with the web studio. */
    val status: String = "study",
    val energy: Int = 3,
    val practice: String = "",
    val source: String = "",
)

@Serializable
data class Gig(val id: String, val title: String, val date: String = "", val time: String = "20:30", val kind: String = "gig", val location: String = "", val setId: String = "", val notes: String = "")

@Serializable
data class Rehearsal(val id: String, val date: String, val seconds: Int, val songIds: List<String> = emptyList(), val notes: String = "")

@Serializable
data class SetItem(
    /** "song" oppure "mark" (segnaposto: pausa, cambio chitarra…) */
    val type: String,
    val id: String? = null,
    val label: String? = null,
    val dur: String? = null,
) {
    val isMark get() = type == "mark"
}

@Serializable
data class SetList(val id: String, val name: String, val items: List<SetItem> = emptyList())

@Serializable
data class Band(
    val id: String,
    val name: String,
    val me: String = "Tutti",
    val songs: List<Song> = emptyList(),
    val sets: List<SetList> = emptyList(),
    val cur: String = "",
    val events: List<Gig> = emptyList(),
    val rehearsals: List<Rehearsal> = emptyList(),
) {
    val curSet: SetList? get() = sets.firstOrNull { it.id == cur } ?: sets.firstOrNull()
    fun song(id: String?): Song? = songs.firstOrNull { it.id == id }
}

@Serializable
data class Options(
    /** "en" (C D E) oppure "it" (Do Re Mi) */
    val notation: String = "en",
    /** "auto", "sharp" o "flat" */
    val accid: String = "auto",
    /** corpo del testo in live, in sp */
    val fs: Int = 22,
    val bar: Boolean = true,
    val timer: Boolean = true,
    /** puntino che pulsa al tempo dei BPM del brano, in live */
    val pulse: Boolean = true,
    /** il "tic" del metronomo, che si accende dal Live toccando l'indicatore del battito */
    val click: Boolean = false,
    val caps: Boolean = false,
    val roots: Boolean = false,
    val plain: Boolean = false,
    val sort: String = "add",
    val liveSet: String = "",
    val liveIndex: Int = 0,
) {
    val italian get() = notation == "it"
}

@Serializable
data class DB(
    val v: Int = 4,
    val bands: List<Band> = emptyList(),
    val curBand: String = "",
    val opt: Options = Options(),
) {
    val band: Band get() = bands.firstOrNull { it.id == curBand } ?: bands.first()
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = true
    // null nel JSON web (campi opzionali) → valore di default
    coerceInputValues = true
}

/** Voce della scaletta già risolta: un brano vero o un segnaposto. */
sealed interface Entry {
    data class Track(val song: Song) : Entry
    data class Mark(val label: String, val dur: String) : Entry
}

fun Band.entries(): List<Entry> = curSet?.items.orEmpty().mapNotNull { it ->
    if (it.isMark) Entry.Mark(it.label ?: "", it.dur ?: "")
    else song(it.id)?.let { s -> Entry.Track(s) }
}
