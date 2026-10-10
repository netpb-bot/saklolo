package ph.saklolo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.util.Log
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

// Speech input (offline Whisper, else Android's SpeechRecognizer) and TTS.
object Voice {
    // ponytail: process-lifetime TTS on the app context, never shut down; fine for one activity
    private var tts: TextToSpeech? = null
    private var ready = false

    private val main = Handler(Looper.getMainLooper())
    private val onDone = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private var n = 0

    // The utterance being spoken, so an open mic drops our own voice (only the newest id clears it: QUEUE_FLUSH stops
    // the old one after the new one is queued). quietAt: when the app last stopped talking, for the room's echo tail.
    @Volatile private var talkingId: String? = null
    @Volatile private var quietAt = 0L
    private fun quiet(id: String?) { if (id == null || talkingId == id) { talkingId = null; quietAt = SystemClock.elapsedRealtime() } }

    fun init(ctx: Context) {
        if (tts == null) tts = TextToSpeech(ctx.applicationContext) { ready = it == TextToSpeech.SUCCESS }.also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                override fun onDone(id: String) { quiet(id); onDone.remove(id)?.let { cb -> main.post { cb() } } }
                @Deprecated("old API") override fun onError(id: String) { quiet(id); onDone.remove(id)?.let { cb -> main.post { cb() } } }
                override fun onStop(id: String, interrupted: Boolean) { quiet(id); onDone.remove(id) } // flushed by newer speech
            })
        }
    }

    // Returns false when this language has no offline voice (Filipino mode is then text only). done() runs on the
    // main thread when the speech finishes, or right away if nothing could be spoken, so the hands-free loop goes on.
    fun speak(text: String, lang: Lang, done: (() -> Unit)? = null): Boolean {
        val t = tts?.takeIf { ready } ?: return false.also { done?.invoke() }
        val ok = t.setLanguage(if (lang == Lang.FIL) Locale.forLanguageTag("fil-PH") else Locale.ENGLISH) >= 0
        if (ok) {
            val id = "u${n++}"
            done?.let { onDone[id] = it }
            talkingId = id
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) quiet(id)
        } else { t.stop(); done?.invoke() }
        return ok
    }

    fun stop() { tts?.stop(); quiet(null) }

    // An open mic ignores the app's own voice and the rescuer's ring, plus a short echo tail after either.
    var echoTailMs = 500 // calibration knob: raise if the phone's speaker rings on in a hard room
    private var ringEnd = 0L
    private fun ownSound(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (Alert.ringing) ringEnd = now
        return talkingId != null || Alert.ringing || now - maxOf(quietAt, ringEnd) < echoTailMs
    }

    private var rec: SpeechRecognizer? = null

    // Offline speech packs. The recogniser only works with no signal if its pack for that language is downloaded;
    // on the S24 neither fil-PH nor en-US was, so every listen failed at once (errors 12/13) and the mic never opened.
    enum class Packs { CHECKING, READY, DOWNLOADING, ONLINE_ONLY, NONE }
    var packs by mutableStateOf(Packs.CHECKING)
        private set
    private var offline = listOf("fil-PH", "en-US") // order to try; trimmed to installed packs once known

    // Android 13+: see which packs are installed and ask the recogniser to download missing ones (needs internet
    // once, like the AI model). Older Android just tries offline, then online.
    fun prepare(ctx: Context) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { packs = Packs.NONE; return }
        if (Build.VERSION.SDK_INT < 33) { packs = Packs.READY; return }
        main.post {
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            val base = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            r.checkRecognitionSupport(base, ctx.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(s: RecognitionSupport) {
                    fun ours(l: List<String>) = l.filter { it.startsWith("fil") || it.startsWith("tl") || it.startsWith("en") }
                    Log.d("Saklolo", "stt packs: installed=${ours(s.installedOnDeviceLanguages)} pending=${ours(s.pendingOnDeviceLanguages)} " +
                        "supported=${ours(s.supportedOnDeviceLanguages)} online=${ours(s.onlineLanguages).size}")
                    val wanted = listOf("fil-PH", "en-US")
                    val missing = wanted.filter { it in s.supportedOnDeviceLanguages && it !in s.installedOnDeviceLanguages && it !in s.pendingOnDeviceLanguages }
                    missing.forEach { r.triggerModelDownload(Intent(base).putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)) }
                    // Any installed Filipino or English pack works now (the S24 had only en-GB); Filipino first.
                    val installed = ours(s.installedOnDeviceLanguages).sortedBy { if (it.startsWith("en")) 1 else 0 }
                    if (installed.isNotEmpty()) offline = installed
                    packs = when {
                        whisper != null || installed.isNotEmpty() -> Packs.READY
                        missing.isNotEmpty() || s.pendingOnDeviceLanguages.any { it in wanted } -> Packs.DOWNLOADING
                        else -> Packs.ONLINE_ONLY
                    }
                    main.postDelayed({ r.destroy() }, 5_000) // let the download requests go out first
                }
                override fun onError(e: Int) { Log.d("Saklolo", "stt packs: check failed $e"); if (packs == Packs.CHECKING) packs = Packs.READY }
            })
        }
    }

    // Closes the mic with no callback (the screen that asked is gone).
    fun cancel() { session++; rec?.destroy(); rec = null }

    // Whisper when its files are sideloaded; else the system recogniser, fil-PH first (handles Taglish), en-US if
    // that fails. Main thread only. Input doesn't follow the EN/FIL setting. onDone(said, failed): said is null when
    // there's no text; failed means the mic or recogniser is broken (not just silence), so callers back off.
    // standby: wait as long as it takes for someone to speak (hands-free; Whisper path only, the system recogniser
    // times out by itself) instead of giving up after noSpeechMs.
    fun listen(ctx: Context, standby: Boolean = false, onDone: (said: String?, failed: Boolean) -> Unit) {
        whisper?.let { return hearWhisper(it, standby, onDone) }
        if (Alert.ringing) return onDone(null, false) // the mic would only hear the alarm
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return onDone(null, true)
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx).also { rec = it }
        // Attempts in order: each offline pack, then fil-PH online (Taglish), used only when the offline ones fail.
        val attempts = offline.map { it to true } + ("fil-PH" to false)
        var at = 0
        fun tag() = attempts[at].first + if (attempts[at].second) "" else " online"
        fun start() = r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, attempts[at].first)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, attempts[at].second)
        })
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle) {
                r.destroy()
                val said = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                Log.d("Saklolo", "stt (${tag()}): $said")
                onDone(said?.takeUnless(Dictionary::noise), false)
            }
            override fun onError(e: Int) {
                Log.d("Saklolo", "stt error $e (${tag()})")
                // Google's offline recogniser has no fil-PH pack (error 12/13); other recognisers fail with
                // NETWORK/SERVER/CLIENT. Retry the next one on anything but "nothing heard" or no permission.
                // NO_MATCH is not "nothing heard": an English pack hearing Tagalog gives error 7.
                val unheard = e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                if (unheard || at == attempts.lastIndex) { r.destroy(); return onDone(null, !unheard && e != SpeechRecognizer.ERROR_NO_MATCH) }
                // Same instance, next loop turn: destroy + recreate here races the old session's teardown (error 11).
                at++
                Handler(Looper.getMainLooper()).post { if (rec === r) start() }
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) { level = ((v - 2) / 10).coerceIn(0f, 1f) * 0.12f } // ponytail: dB-ish, ~2 quiet → 12 loud; the waveform knob
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        start()
    }

    // Offline Whisper (sherpa-onnx), sideloaded like EmbeddingGemma: small-encoder.int8.onnx, small-decoder.int8.onnx,
    // small-tokens.txt. Google's on-device recogniser has no Filipino pack (S24: en-* only), so Tagalog got error 7.
    const val WHISPER = "small" // calibration knob: "base" if small is too slow on a phone
    @Volatile private var whisper: OfflineRecognizer? = null
    @Volatile private var session = 0 // bumped by cancel(): a stale recording stops and its result is dropped

    // Calibration knobs. maxMs counts from the first speech, so a standby wait can be any length.
    @Volatile var level = 0f // mic loudness (rms) for the waveform; 0 when the mic is closed
    var speechRms = 0.02f; var endSilenceMs = 1200; var maxMs = 15_000; var noSpeechMs = 6_000
    var standbyMs = 60_000 // a hands-free wait gives up after this much silence; the mic button then shows "Mic off"

    // Silero VAD (sherpa-onnx, silero_vad.onnx sideloaded next to Whisper) tells speech from rain, wind and rubble, so
    // an always-open mic doesn't wake Whisper on noise. Missing file → energy endpointing (speechRms), as before.
    const val VAD_FILE = "silero_vad.onnx"
    var vadThreshold = 0.5f // calibration knob: higher if noise still counts as speech
    @Volatile private var vad: Vad? = null
    val vadReady get() = vad != null
    val whisperReady get() = whisper != null
    @Volatile var sttMs = 0L // last Whisper decode, for the judge view

    // App start, never on mic tap. Missing files → the system recogniser path, as before.
    fun loadWhisper(ctx: Context) {
        val dir = ctx.getExternalFilesDir(null)
        thread {
            vad = runCatching {
                val f = File(dir, VAD_FILE).also { require(it.exists()) { "missing $it" } }
                Vad(config = VadModelConfig(sileroVadModelConfig = SileroVadModelConfig(model = f.path, threshold = vadThreshold,
                    minSilenceDuration = 0.25f, minSpeechDuration = 0.25f, windowSize = VAD_WINDOW, maxSpeechDuration = 30f), sampleRate = 16_000, numThreads = 1))
            }.onFailure { Log.w("Saklolo", "vad not loaded (energy endpointing instead): $it") }.getOrNull()
            val t0 = SystemClock.uptimeMillis()
            fun f(s: String) = File(dir, "$WHISPER-$s").also { require(it.exists()) { "missing $it" } }.path
            whisper = runCatching {
                OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = OfflineModelConfig(
                    // language "" = detect per utterance (en, tl), so English, Filipino and Taglish all work
                    whisper = OfflineWhisperModelConfig(encoder = f("encoder.int8.onnx"), decoder = f("decoder.int8.onnx"), language = ""),
                    tokens = f("tokens.txt"), numThreads = 4, modelType = "whisper",
                )))
            }.onFailure { Log.w("Saklolo", "whisper not loaded: $it") }.getOrNull()
            if (whisper != null) {
                Log.d("Saklolo", "whisper: loaded $WHISPER in ${SystemClock.uptimeMillis() - t0} ms")
                main.post { packs = Packs.READY }
            }
        }
    }

    private class MicFailed(msg: String) : Exception(msg)

    private fun hearWhisper(w: OfflineRecognizer, standby: Boolean, onDone: (String?, Boolean) -> Unit) {
        val me = ++session
        thread {
            var failed = false
            val said = runCatching {
                record(me, standby)?.let { pcm ->
                    val t0 = SystemClock.uptimeMillis()
                    synchronized(w) {
                        val s = w.createStream()
                        try { s.acceptWaveform(pcm, 16_000); w.decode(s); w.getResult(s) } finally { s.release() }
                    }.also { sttMs = SystemClock.uptimeMillis() - t0; Log.d("Saklolo", "stt (whisper ${it.lang}, ${pcm.size / 16} ms audio, $sttMs ms): ${it.text}") }
                        .let { r ->
                            // Room noise comes back as a few words in a random language ("Seguiu, Pô?" = pt). Real reports
                            // are long, so a mislabelled Taglish report still gets through. ponytail: 4 words is the knob.
                            val lang = r.lang.trim('<', '|', '>', ' ')
                            val text = r.text.trim()
                            if (lang !in setOf("en", "tl", "") && text.split(' ').size < 4) "".also { Log.d("Saklolo", "stt: dropped short $lang: $text") } else text
                        }
                }
            }.onFailure { failed = it is MicFailed; Log.w("Saklolo", "whisper failed: $it") }.getOrNull()
                ?.takeUnless { it.isBlank() || Dictionary.noise(it).also { n -> if (n) Log.d("Saklolo", "stt: dropped as noise: $it") } }
            main.post { if (session == me) onDone(said, failed) }
        }
    }

    private const val VAD_WINDOW = 512 // Silero's frame at 16 kHz (32 ms)
    private const val PRE_ROLL = 16 // frames (~0.5 s) kept before speech is detected, so the first word isn't cut

    // 16 kHz mono until ~1.2 s of quiet after speech. Null: nothing said or cancelled. Throws MicFailed when the mic
    // can't be opened or read (permission revoked, another app holds it).
    @SuppressLint("MissingPermission") // MicButton asks for RECORD_AUDIO before listening
    private fun record(me: Int, standby: Boolean): FloatArray? {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val ar = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate)) }
            .getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED } ?: throw MicFailed("mic would not open")
        val v = vad
        val buf = ShortArray(VAD_WINDOW)
        val chunks = ArrayDeque<FloatArray>()
        var heard = false; var quiet = 0; var waited = 0; var spoke = 0; var peak = 0f
        val step = VAD_WINDOW * 1000 / rate
        fun reset() { chunks.clear(); heard = false; quiet = 0; v?.let { synchronized(it) { it.reset() } } }
        try {
            reset()
            ar.startRecording()
            if (ar.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicFailed("mic would not start")
            while (session == me && spoke < maxMs) {
                var n = 0
                while (n < buf.size) { val r = ar.read(buf, n, buf.size - n); if (r < 0) throw MicFailed("mic read $r"); if (r == 0) break; n += r }
                if (n == 0) throw MicFailed("mic gave no audio")
                // Our own voice or the ring: drop everything so far (can't separate it from the victim's).
                if (ownSound()) { level = 0f; if (chunks.isNotEmpty()) reset(); continue }
                val c = FloatArray(n) { buf[it] / 32768f }
                chunks.addLast(c)
                val rms = kotlin.math.sqrt(c.fold(0f) { a, x -> a + x * x } / n).also { peak = maxOf(peak, it); level = it }
                val speech = if (v != null) synchronized(v) { v.acceptWaveform(c); v.isSpeechDetected().also { v.clear() } } else rms > speechRms
                if (speech) { heard = true; quiet = 0 } else if (heard) quiet += step
                if (heard) spoke += step else {
                    while (chunks.size > PRE_ROLL) chunks.removeFirst() // waiting: keep only the pre-roll
                    waited += step
                    if (waited >= if (standby) standbyMs else noSpeechMs) break
                }
                if (heard && quiet >= endSilenceMs) break
            }
        } finally { runCatching { ar.stop() }; ar.release(); level = 0f }
        Log.d("Saklolo", "mic: waited $waited ms, spoke $spoke ms, heard=$heard, peak rms=$peak (${if (v != null) "vad" else "energy $speechRms"})")
        if (session != me || !heard) return null
        val pcm = FloatArray(chunks.sumOf { it.size }); var i = 0
        chunks.forEach { it.copyInto(pcm, i); i += it.size }
        return pcm
    }
}
