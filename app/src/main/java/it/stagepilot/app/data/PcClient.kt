package it.stagepilot.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable

class SyncConflict(val paths: List<String>, val revision: Int) : Exception("Le stesse informazioni sono cambiate sul PC e sul tablet.")
@Serializable private data class SyncRequest(val db: DB, val base: DB? = null, val policy: String? = null, val revision: Int? = null, val delivery: Boolean = false)
@Serializable private data class SyncResponse(val db: DB? = null, val conflicts: List<String> = emptyList(), val revision: Int = 0, val error: String = "")
@Serializable private data class MetadataRequest(val body: String, val title: String, val artist: String)
@Serializable data class MetadataSource(val label: String = "", val url: String = "")
@Serializable data class MetadataCandidate(val title: String = "", val artist: String = "", val album: String = "", val dur: String = "", val bpm: Int? = null, val recordingUrl: String = "", val sources: List<MetadataSource> = emptyList())
@Serializable data class MetadataResult(val found: Boolean = false, val selected: MetadataCandidate? = null, val candidates: List<MetadataCandidate> = emptyList(), val message: String = "", val error: String = "")
@Serializable private data class TransferPoll(val device: String, val ready: Boolean)
@Serializable private data class TransferFinish(val device: String, val sequence: Int, val state: String)
@Serializable private data class TransferTicket(val sequence: Int = 0)

/** Un errore da dire a chi usa l'app, in italiano. */
class PcException(message: String) : Exception(message)

/* Scarica la coda dei brani dalla pagina del PC (tools/pagina-pc), nella rete di casa. Il PC dà un
   codice di 4 cifre: senza, la coda non si legge. Solo HTTP in chiaro: è la rete di casa. */
object PcClient {
    suspend fun pollTransfer(address: String, code: String, device: String, ready: Boolean): Int = withContext(Dispatchers.IO) {
        val c = open(address, code, "/api/transfer/poll", post = true)
        try {
            c.outputStream.use { it.write(AppJson.encodeToString(TransferPoll.serializer(), TransferPoll(device, ready)).toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) return@withContext 0
            AppJson.decodeFromString(TransferTicket.serializer(), c.inputStream.bufferedReader().use { it.readText() }).sequence
        } finally { c.disconnect() }
    }
    suspend fun finishTransfer(address: String, code: String, device: String, sequence: Int, state: String) = withContext(Dispatchers.IO) {
        val c = open(address, code, "/api/transfer/finish", post = true)
        try {
            c.outputStream.use { it.write(AppJson.encodeToString(TransferFinish.serializer(), TransferFinish(device, sequence, state)).toByteArray(Charsets.UTF_8)) }
            c.responseCode == 200
        } finally { c.disconnect() }
    }
    suspend fun identify(address: String, code: String, body: String, title: String, artist: String): MetadataResult = withContext(Dispatchers.IO) {
        guard {
            val c = open(address, code, "/api/metadata", post = true)
            c.readTimeout = 30000
            try {
                c.outputStream.use { it.write(AppJson.encodeToString(MetadataRequest.serializer(), MetadataRequest(body, title, artist)).toByteArray(Charsets.UTF_8)) }
                val status = c.responseCode
                val text = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = runCatching { AppJson.decodeFromString(MetadataResult.serializer(), text) }.getOrNull()
                    ?: throw PcException("Aggiorna StagePilot Studio sul PC per il riconoscimento online.")
                if (status != 200) throw PcException(result.error.ifBlank { "Ricerca non disponibile. Puoi compilare i dati a mano." })
                result
            } finally { c.disconnect() }
        }
    }
    suspend fun sync(address: String, code: String, current: DB, base: String?, policy: String? = null, revision: Int? = null, delivery: Boolean = false): DB = withContext(Dispatchers.IO) {
        guard {
            val c = open(address, code, "/api/sync", post = true)
            try {
                val request = SyncRequest(current, base?.let(::decodeBackup), policy, revision, delivery)
                c.outputStream.use { it.write(AppJson.encodeToString(SyncRequest.serializer(), request).toByteArray(Charsets.UTF_8)) }
                val status = c.responseCode
                val text = (if (status < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val response = runCatching { AppJson.decodeFromString(SyncResponse.serializer(), text) }.getOrNull()
                    ?: throw PcException("Il PC non sta eseguendo StagePilot Studio. Apri StagePilot.cmd sul PC.")
                if (status == 409 && response.conflicts.isNotEmpty()) throw SyncConflict(response.conflicts, response.revision)
                if (status != 200) throw PcException(response.error.ifBlank { "Il PC non ha accettato la sincronizzazione ($status)." })
                response.db ?: throw PcException("Il PC non ha restituito una libreria valida.")
            } finally { c.disconnect() }
        }
    }
    fun baseUrl(address: String): String {
        val a = address.trim().removePrefix("http://").trimEnd('/')
        return "http://" + if (':' in a) a else "$a:8765"
    }

    private fun open(address: String, code: String, path: String, post: Boolean): HttpURLConnection {
        val c = URL(baseUrl(address) + path).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 6000
        c.setRequestProperty("X-Code", code.trim())
        if (post) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
        }
        return c
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: PcException) {
        throw e
    } catch (e: IOException) {
        throw PcException("Non trovo il PC. La pagina è aperta e telefono e PC sono sulla stessa Wi-Fi?")
    } catch (e: IllegalArgumentException) {
        throw PcException("L'indirizzo non è valido: deve essere come 192.168.178.32:8765")
    }

    suspend fun fetch(address: String, code: String): PcInbox = withContext(Dispatchers.IO) {
        guard {
            val c = open(address, code, "/api/inbox", post = false)
            try {
                when (c.responseCode) {
                    200 -> parsePcInboxFull(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                    403 -> throw PcException("Il codice non è giusto: guardalo sulla pagina del PC.")
                    else -> throw PcException("Il PC ha risposto in modo strano (${c.responseCode}).")
                }
            } finally {
                c.disconnect()
            }
        }
    }

    /** Dice al PC che questi brani sono arrivati: escono dalla coda. */
    suspend fun ack(address: String, code: String, ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) guard {
            val c = open(address, code, "/api/inbox/ack", post = true)
            try {
                val body = "{\"ids\":[" + ids.joinToString(",") { "\"" + it.filter { ch -> ch.isLetterOrDigit() } + "\"" } + "]}"
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                c.responseCode // forza l'invio
            } finally {
                c.disconnect()
            }
        }
    }

    /** Manda al PC una copia di sicurezza del database (la pagina ne tiene le ultime). */
    suspend fun uploadBackup(address: String, code: String, json: String) = withContext(Dispatchers.IO) {
        guard {
            val c = open(address, code, "/api/backup", post = true)
            try {
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                if (c.responseCode != 200) throw PcException("Il PC non ha accettato il backup (${c.responseCode}).")
            } finally {
                c.disconnect()
            }
        }
    }
}
