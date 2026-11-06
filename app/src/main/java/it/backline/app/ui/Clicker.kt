package it.backline.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/* Il "tic" del metronomo: due suoni brevi generati qui (niente file audio), uno più acuto per il primo
   battito di ogni quattro. Un solo AudioTrack in streaming resta aperto; a ogni battito ci si scrive il colpo
   (negli intervalli suona silenzio). Gli AudioTrack "statici" su alcuni tablet danno errore -22, lo streaming no. */
class Clicker {
    private val accent = burst(1900, 45)
    private val normal = burst(1100, 35)

    // se il dispositivo non riesce ad aprire l'uscita audio, il click resta muto: l'app non deve mai chiudersi per questo
    private val track: AudioTrack? = runCatching {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(max(min, 16384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }.getOrNull()

    fun click(strong: Boolean) {
        val data = if (strong) accent else normal
        runCatching { track?.write(data, 0, data.size, AudioTrack.WRITE_NON_BLOCKING) }
    }

    fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
    }

    private companion object {
        const val RATE = 48000

        /** Un sinusoide che si spegne in fretta: suona come un colpetto secco. */
        fun burst(freq: Int, ms: Int): ShortArray {
            val n = RATE * ms / 1000
            return ShortArray(n) { i ->
                val env = exp(-5.0 * i / n)
                (sin(2 * PI * freq * i / RATE) * env * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
