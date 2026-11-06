package it.backline.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/* Il metronomo sonoro. Un thread scrive di continuo nell'AudioTrack in streaming: silenzio, e un colpo breve a
   ogni battito (più acuto sul primo di ogni quattro). Il tempo si calcola contando i campioni scritti, non con
   timer: così non scivola e non dipende dalla fretta dell'interfaccia. Il flusso non si ferma mai tra un colpo e
   l'altro: un AudioTrack che resta senza dati si blocca e riparte solo a buffer pieno, e i primi colpi uscirebbero
   tutti insieme in ritardo. */
class Clicker {
    private val accent = burst(1900, 45)
    private val normal = burst(1100, 35)

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Quanto passa, grosso modo, fra la scrittura di un colpo e il suono: serve ad allineare il lampeggio. */
    @Volatile var latencyMs = 0L
        private set

    /** Parte da un primo battito accentato. Se già suonava, riparte da capo (ad esempio al cambio dei BPM). */
    fun start(bpm: Int) {
        stop()
        // se il dispositivo non riesce ad aprire l'uscita audio, il click resta muto: l'app non deve mai chiudersi per questo
        val min = runCatching { AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) }.getOrDefault(0)
        // prima si chiede la latenza più bassa e il buffer più piccolo; se il dispositivo rifiuta, si ripiega
        fun build(lowLatency: Boolean, bytes: Int): AudioTrack = AudioTrack.Builder()
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
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply { if (lowLatency) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
            .build()
        val small = max(min, 4096)
        var bytes = small
        val track = runCatching { build(true, small) }.getOrNull()
            ?: runCatching { build(false, small) }.getOrNull()
            ?: runCatching { bytes = small * 2; build(false, bytes) }.getOrNull()
            ?: return
        latencyMs = track.bufferSizeInFrames * 1000L / RATE
        running = true
        thread = Thread({
            try {
                track.play()
                val perBeat = RATE * 60.0 / bpm
                val chunk = ShortArray(CHUNK)
                var pos = 0L
                var nextClick = 0.0
                var beat = 0
                var cur: ShortArray? = null
                var idx = 0
                while (running) {
                    for (i in 0 until CHUNK) {
                        if (pos + i >= nextClick) {
                            cur = if (beat % 4 == 0) accent else normal
                            idx = 0
                            beat++
                            nextClick += perBeat
                        }
                        val c = cur
                        chunk[i] = if (c != null && idx < c.size) c[idx++] else 0
                    }
                    pos += CHUNK
                    if (track.write(chunk, 0, CHUNK) < 0) break
                }
            } catch (_: Exception) {
            } finally {
                runCatching { track.pause(); track.flush() }
                runCatching { track.release() }
            }
        }, "backline-click").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    private companion object {
        const val RATE = 48000
        const val CHUNK = 480          // 10 ms

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
