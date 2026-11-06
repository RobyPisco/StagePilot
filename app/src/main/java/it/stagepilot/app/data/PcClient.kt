package it.stagepilot.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Un errore da dire a chi usa l'app, in italiano. */
class PcException(message: String) : Exception(message)

/* Scarica la coda dei brani dalla pagina del PC (tools/pagina-pc), nella rete di casa. Il PC dà un
   codice di 4 cifre: senza, la coda non si legge. Solo HTTP in chiaro: è la rete di casa. */
object PcClient {
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
