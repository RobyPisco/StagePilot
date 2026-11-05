package it.backline.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* Copia di sicurezza automatica. Il database sta nella memoria privata dell'app: se la
   disinstalli o il tablet si rompe, sparisce tutto. Quindi:
     - una copia al giorno (le ultime 7) in `Android/data/it.backline.app/files/backups`, che dal PC
       si legge anche con `adb pull` (tools\backup.ps1);
     - una copia sul PC, se la pagina è configurata: parte quando l'app si apre e il database è cambiato. */
object AutoBackup {
    private const val KEEP = 7

    fun dir(context: Context): File? =
        context.getExternalFilesDir("backups")?.also { it.mkdirs() }

    /** Scrive la copia di oggi (sovrascrive quella già fatta oggi) e toglie le più vecchie. */
    fun saveLocal(context: Context, json: String): File? = runCatching {
        val d = dir(context) ?: return null
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val f = File(d, "backline-$stamp.json")
        if (!f.exists() || f.readText() != json) f.writeText(json)
        d.listFiles { x -> x.name.startsWith("backline-") && x.name.endsWith(".json") }
            ?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        f
    }.getOrNull()

    fun hash(json: String): String =
        MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
