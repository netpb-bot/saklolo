package ph.saklolo

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.concurrent.thread

// One loaded model, three closed-list tasks. EmbeddingGemma 300M (~180 MB, CPU) turns text into a meaning vector.
// Reports: small classifier heads trained on labeled examples (train/, assets/heads.json) read hazard, trapped,
// conditions and vulnerable people. Answers and FAQs: the nearest item we wrote, or nothing. It never writes text.
// Gemma 3 1B (generative) failed the Eval set on the S24 on 2026-10-10; this runs on 3–4 GB phones too.
object Llm {
    enum class State { LOADING, READY, MISSING }

    // Bundled in the APK from ../models, or sideloaded to /sdcard/Android/data/ph.saklolo/files/ (wins if present).
    const val MODEL_FILE = "embeddinggemma-300M_seq256_mixed-precision.tflite"
    const val TOKENIZER_FILE = "sentencepiece.model"
    private const val SEQ = 256
    private const val PREFIX = "task: sentence similarity | query: "

    // Calibration knobs (cosine similarity) for answer() and matchFaq(). Report thresholds live in heads.json.
    // First S24 run (2026-10-10): right FAQ matches scored >= 0.81; the wrong one 0.67.
    var minAnswer = 0.60f; var minFaq = 0.75f; var margin = 0.03f

    // Bars the AI must clear to add to a report, on top of each head's own threshold. Thresholds picked on synthetic
    // validation data were far too low for real wording (wrong UNCONSCIOUS on 3 test reports), so these come from
    // train/simulate.py over all four Eval sets (2026-10-10). Rules decide first; the AI only fills what they left.
    private val bars = mapOf("hazard" to 0.97f, "trapped" to 0.95f) +
        Cond.entries.associate { it.name to 0.90f } + Vuln.entries.associate { it.name to 0.95f }

    var state by mutableStateOf(State.LOADING)
        private set
    var name by mutableStateOf("EmbeddingGemma 300M")
        private set
    private var interp: Interpreter? = null
    private var spm: Spm? = null
    private var examples: Map<String, Map<String, List<String>>> = emptyMap()
    private var heads: Heads? = null
    private val cache = HashMap<String, FloatArray>() // ponytail: unbounded per process; a report session is small

    // Called at app start, never on mic tap. Example phrases are embedded right after, in the background.
    fun load(ctx: Context, protocols: Protocols) {
        if (state == State.READY) return
        state = State.LOADING
        examples = protocols.examples
        heads = runCatching { Heads.parse(ctx.assets.open("heads.json").bufferedReader().use { it.readText() }).atLeast(bars) }
            .onFailure { Log.w("Saklolo", "no heads: $it") }.getOrNull()
        val dir = ctx.getExternalFilesDir(null)
        thread {
            runCatching {
                // Sideloaded files win; else the copy bundled in the APK (stored uncompressed, so it maps in place).
                val tok = File(dir, TOKENIZER_FILE)
                spm = Spm(if (tok.exists()) tok.readBytes() else ctx.assets.open(TOKENIZER_FILE).use { it.readBytes() })
                val opts = Interpreter.Options().setNumThreads(4)
                val model = File(dir, MODEL_FILE)
                interp = if (model.exists()) Interpreter(model, opts)
                else ctx.assets.openFd(MODEL_FILE).use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { Interpreter(it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength), opts) }
                }
            }.onFailure { Log.w("Saklolo", "model not loaded: $it") }
            state = if (interp != null && spm != null) State.READY else State.MISSING
            if (state == State.READY) {
                // Example vectors are saved once (app-private storage) so later starts skip the ~40 s warm-up.
                // Only our own example phrases are saved, never what a victim said.
                val t0 = SystemClock.uptimeMillis()
                val all = (examples.values.flatMap { it.values.flatten() } + protocols.all.values.flatMap { p -> p.faqs.flatMap { it.examples } }).distinct()
                val saved = File(ctx.filesDir, "examples-${(MODEL_FILE + PREFIX).hashCode()}.bin")
                runCatching { readVectors(saved) }
                val missing = all.filter { it !in cache }
                missing.forEach(::embed)
                if (missing.isNotEmpty()) runCatching { writeVectors(saved, all) }.onFailure { Log.w("Saklolo", "couldn't save vectors: $it") }
                Log.d("Saklolo", "warm-up: ${all.size} examples, ${missing.size} computed, in ${SystemClock.uptimeMillis() - t0} ms")
            }
        }
    }

    @Synchronized
    private fun readVectors(f: File) = java.io.DataInputStream(f.inputStream().buffered()).use { s ->
        repeat(s.readInt()) { val k = s.readUTF(); cache[k] = FloatArray(s.readInt()) { s.readFloat() } }
    }

    @Synchronized
    private fun writeVectors(f: File, keys: List<String>) = java.io.DataOutputStream(f.outputStream().buffered()).use { s ->
        s.writeInt(keys.size)
        keys.forEach { k -> val v = cache.getValue(k); s.writeUTF(k); s.writeInt(v.size); v.forEach(s::writeFloat) }
    }

    @Synchronized
    private fun embed(text: String): FloatArray = cache.getOrPut(text) {
        val m = interp!!
        val out = Array(1) { FloatArray(m.getOutputTensor(0).shape().last()) }
        m.run(arrayOf(spm!!.input(PREFIX + text, SEQ)), out)
        unit(out[0])
    }

    // Best similarity per label: the closest of its example phrases.
    private fun scores(text: String, labels: Map<String, List<String>>): Map<String, Float> {
        val v = embed(text)
        return labels.mapValues { (_, ex) -> ex.maxOf { dot(v, embed(it)) } }
    }

    private fun Map<String, Float>.show() = entries.sortedByDescending { it.value }.take(3).joinToString(" ") { "%s %.2f".format(it.key, it.value) }

    // The AI's own reading of a report (heads only, no rules), or null if not loaded. Whole report + each clause.
    fun understand(text: String): Pair<Facts, HeadOut>? {
        val h = heads?.takeIf { state == State.READY } ?: return null
        val parts = clauses(text)
        val out = applyHeads(h, (listOf(text) + if (parts.size > 1) parts else emptyList()).map(::embed))
        val conds = out.labels.mapNotNull { l -> Cond.entries.firstOrNull { it.name == l } }.toSet()
        return Facts(
            hazard = out.picks["hazard"]?.takeIf { it != "NONE" }?.let(Hazard::valueOf),
            trapped = when (out.picks["trapped"]) { "YES" -> true; "NO" -> false; else -> null },
            vuln = out.labels.mapNotNull { l -> Vuln.entries.firstOrNull { it.name == l } }.toSet(),
            // Safety: someone describing their own breathing is breathing, so never NOT_BREATHING (CPR) then.
            cond = if (Dictionary.breathingTrouble(text)) conds - Cond.NOT_BREATHING else conds,
        ) to out
    }

    // Blocking; call off the main thread. Rules decide what they found (a clear "gumuho" beats a guess) and give the
    // exact fields (count, floor, room); the AI adds what the word lists missed, only above its bar.
    fun extract(text: String): Pair<Facts?, String> {
        if (state != State.READY) return null to "(model not ready)"
        val t0 = SystemClock.uptimeMillis()
        val rules = Dictionary.extract(text)
        val (ai, out) = understand(text) ?: return rules to "(no heads: rules only)\nrules $rules"
        val facts = rules.copy(
            hazard = rules.hazard ?: ai.hazard, trapped = rules.trapped ?: ai.trapped,
            cond = rules.cond + ai.cond, vuln = rules.vuln + ai.vuln,
        )
        val top = out.p.entries.filter { it.value >= 0.3f && !it.key.endsWith(".NONE") && !it.key.endsWith(".UNKNOWN") }
            .sortedByDescending { it.value }.joinToString(" ") { "%s %.2f".format(it.key.substringAfter('.'), it.value) }
        val debug = "AI $ai\nrules $rules\np: $top"
        Log.d("Saklolo", "extract ${SystemClock.uptimeMillis() - t0} ms: $debug")
        return facts to debug
    }

    // Blocking; YES/NO/UNSURE when one is clearly nearest, or null (the caller still combines with the dictionary).
    fun answer(question: String, said: String): Ans? {
        if (state != State.READY) return null
        val s = scores(said, examples.getValue("answer"))
        Log.d("Saklolo", "answer \"$said\": ${s.show()}")
        return nearest(s, minAnswer, margin)?.let(Ans::valueOf)
    }

    // Blocking; one of the given FAQ ids or NO_FAQ, or null if not loaded. Only the current protocol's FAQs.
    fun matchFaq(question: String, faqs: List<Faq>): String? {
        if (state != State.READY) return null
        val s = scores(question, faqs.associate { it.id to it.examples })
        Log.d("Saklolo", "matchFaq \"$question\": ${s.show()}")
        return nearest(s, minFaq, margin) ?: NO_FAQ
    }
}

// SentencePiece BPE encoder for EmbeddingGemma's sentencepiece.model (Google ships C++ only). Pure Kotlin, JVM-tested
// against the official Python sentencepiece. Reads just the protobuf fields it needs.
// ponytail: O(n²) merge loop per text; fine for one short report, a heap if we ever embed long documents
class Spm(model: ByteArray) {
    private val ids = HashMap<String, Int>()
    private val scores = HashMap<String, Float>()
    private val userDefined = HashSet<String>()
    private var maxUser = 0
    private var dummyPrefix = true
    private var collapseSpaces = true
    val bos: Int; val eos: Int; val pad: Int; private val unk: Int

    init {
        var i = 0
        Proto(model).fields { field, r ->
            when (field) {
                1 -> { // SentencePiece { 1: piece, 2: score, 3: type }
                    var piece = ""; var score = 0f; var type = 1
                    r.message().fields { f, p ->
                        when (f) { 1 -> piece = p.string(); 2 -> score = p.float(); 3 -> type = p.varint().toInt(); else -> p.skip() }
                    }
                    if (type != 5) { ids[piece] = i; scores[piece] = score } // 5 = UNUSED
                    if (type == 4) { userDefined += piece; maxUser = maxOf(maxUser, piece.length) }
                    i++
                }
                3 -> r.message().fields { f, p -> // NormalizerSpec { 3: add_dummy_prefix, 4: remove_extra_whitespaces }
                    when (f) { 3 -> dummyPrefix = p.varint() != 0L; 4 -> collapseSpaces = p.varint() != 0L; else -> p.skip() }
                }
                else -> r.skip()
            }
        }
        bos = ids.getValue("<bos>"); eos = ids.getValue("<eos>"); pad = ids.getValue("<pad>"); unk = ids.getValue("<unk>")
    }

    fun encode(text: String): List<Int> {
        var s = if (collapseSpaces) text.trim().replace(Regex(" +"), " ") else text
        if (dummyPrefix) s = " $s"
        s = s.replace(' ', '▁')
        // Initial symbols: user-defined pieces whole (longest match), otherwise one code point each.
        val sym = ArrayList<String>()
        var k = 0
        while (k < s.length) {
            val user = (minOf(maxUser, s.length - k) downTo 2).firstNotNullOfOrNull { n -> s.substring(k, k + n).takeIf { it in userDefined } }
            val step = user?.length ?: Character.charCount(s.codePointAt(k))
            sym += s.substring(k, k + step); k += step
        }
        // Merge the adjacent pair whose join has the best score; ties go to the leftmost.
        while (true) {
            var best = -1; var bestScore = Float.NEGATIVE_INFINITY
            for (j in 0 until sym.size - 1) {
                val sc = scores[sym[j] + sym[j + 1]] ?: continue
                if (sc > bestScore) { bestScore = sc; best = j }
            }
            if (best < 0) break
            sym[best] = sym[best] + sym.removeAt(best + 1)
        }
        // Unknown symbols fall back to their UTF-8 bytes (<0xAB> pieces).
        return sym.flatMap { p -> ids[p]?.let(::listOf) ?: p.toByteArray().map { b -> ids["<0x%02X>".format(b.toInt() and 0xFF)] ?: unk } }
    }

    // Model input: <bos> tokens <eos>, truncated and padded to the model's fixed length (as Google's sample does).
    fun input(text: String, len: Int): IntArray {
        val t = encode(text).take(len - 2)
        return (listOf(bos) + t + eos).toIntArray().copyOf(len).also { it.fill(pad, t.size + 2, len) }
    }
}

// Minimal protobuf reader: varints, 32-bit floats, length-delimited fields.
private class Proto(private val b: ByteArray, private var pos: Int = 0, private val end: Int = b.size) {
    private var wire = 0
    fun fields(on: (Int, Proto) -> Unit) {
        while (pos < end) { val key = varint().toInt(); wire = key and 7; on(key ushr 3, this) }
    }
    fun varint(): Long {
        var r = 0L; var shift = 0
        while (true) { val x = b[pos++].toInt(); r = r or ((x and 0x7F).toLong() shl shift); if (x >= 0) return r; shift += 7 }
    }
    fun float(): Float = java.nio.ByteBuffer.wrap(b, pos, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).float.also { pos += 4 }
    private fun bytes(): IntRange { val n = varint().toInt(); return (pos until pos + n).also { pos += n } }
    fun string(): String = bytes().let { String(b, it.first, it.last - it.first + 1, Charsets.UTF_8) }
    fun message(): Proto = bytes().let { Proto(b, it.first, it.last + 1) }
    fun skip() { when (wire) { 0 -> varint(); 1 -> pos += 8; 2 -> bytes(); 5 -> pos += 4; else -> error("wire $wire") } }
}
