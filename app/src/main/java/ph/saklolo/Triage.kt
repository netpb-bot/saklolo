package ph.saklolo

import org.json.JSONObject

// Pure rules: no Android imports. Triage colour is a rule, never the model (PRD D2).

enum class Vuln { INFANT, CHILD, ELDERLY, PREGNANT, PWD }
// Order is the PRD §8 spot nibble.
enum class Spot { UNKNOWN, BATHROOM, STAIRS, UNDER_TABLE, ROOM, KITCHEN, HALLWAY, ATTIC, OUTSIDE }
enum class Tri { GREEN, YELLOW, RED }

const val ROOF = 15

// null / 0 / UNKNOWN mean "not known yet".
data class Facts(
    val hazard: Hazard? = null, val people: Int = 0, val trapped: Boolean? = null,
    val floor: Int = 0, val spot: Spot = Spot.UNKNOWN,
    val vuln: Set<Vuln> = emptySet(), val cond: Set<Cond> = emptySet(),
) {
    fun merge(n: Facts) = Facts(
        n.hazard ?: hazard, if (n.people > 0) n.people else people, n.trapped ?: trapped,
        if (n.floor > 0) n.floor else floor, if (n.spot != Spot.UNKNOWN) n.spot else spot,
        vuln + n.vuln, cond + n.cond,
    )
}

fun triage(f: Facts): Tri = when {
    f.cond.any { it == Cond.NOT_BREATHING || it == Cond.UNCONSCIOUS || it == Cond.HEAVY_BLEEDING } -> Tri.RED
    f.trapped == true && Cond.WATER_RISING in f.cond -> Tri.RED
    // Any condition counts: rising water is a hazard even before the hazard code is known.
    f.trapped == true || f.cond.isNotEmpty() || f.vuln.isNotEmpty() || f.hazard != null -> Tri.YELLOW
    f == Facts() -> Tri.YELLOW // bare SOS
    else -> Tri.GREEN
}

// Decided only on the victim phone: filling in an unknown is never worse.
fun worse(old: Facts, new: Facts): Boolean =
    ((old.hazard != null || old.cond.isNotEmpty()) && (new.cond - old.cond).isNotEmpty()) ||
        (old.people > 0 && new.people > old.people) ||
        (old.trapped == false && new.trapped == true)

// Missing required facts as follow-up ids (texts in protocols.json "followups"), in dispatcher order:
// what happened, anyone hurt, trapped, how many, where.
fun missing(f: Facts): List<String> = buildList {
    if (f.hazard == null) add("what")
    if (f.cond.none { it in injuries }) add("hurt") // "the water is rising" doesn't say whether anyone is hurt
    if (f.trapped == null) add("exit")
    if (f.people == 0) add("count")
    // Where rescuers look: the roof or floor in a flood, the spot in rubble. The texts differ per hazard.
    when (f.hazard) {
        Hazard.FLOOD -> if (f.floor == 0) add("place")
        Hazard.COLLAPSE, Hazard.LANDSLIDE -> if (f.floor == 0 && f.spot == Spot.UNKNOWN) add("place")
        else -> {}
    }
}

val injuries = setOf(Cond.NOT_BREATHING, Cond.UNCONSCIOUS, Cond.HEAVY_BLEEDING, Cond.PINNED, Cond.INJURED)

// Every required fact, one question each; most reports already answer some, so fewer get asked.
const val MAX_FOLLOWUPS = 5

fun followUps(f: Facts) = missing(f).take(MAX_FOLLOWUPS)

// "Can you get out?": anything but a clear YES counts as trapped (the safer reading).
fun Facts.withExit(a: Ans) = copy(trapped = a != Ans.YES)

// Model answer → YES/NO/UNSURE, or null unless exactly one label appears.
fun validateAnswer(raw: String): Ans? =
    Regex("\\b(YES|NO|UNSURE)\\b").findAll(raw.uppercase()).map { it.value }.toSet().singleOrNull()?.let(Ans::valueOf)

// Model and dictionary must agree; any disagreement is UNSURE (the safer branch). Measured on the S24, Gemma 3 1B
// read hedges like "parang oo, parang hindi" as YES, so the model alone never decides.
// ponytail: the model can only confirm or downgrade; trust it alone again if a bigger model passes the Eval set
fun combine(model: Ans?, dict: Ans): Ans = when (model) {
    null, dict -> dict
    else -> Ans.UNSURE
}

const val NO_FAQ = "NONE"

// ---- Embedding matching (pure; the model only supplies the vectors) ----

fun unit(v: FloatArray): FloatArray = Math.sqrt(v.sumOf { it.toDouble() * it }).toFloat().let { n -> FloatArray(v.size) { v[it] / n } }
fun dot(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

// The best label, or null when it isn't close enough or barely beats the runner-up: a miss is safer than a wrong pick.
fun nearest(sims: Map<String, Float>, min: Float, margin: Float): String? {
    val s = sims.entries.sortedByDescending { it.value }
    val best = s.firstOrNull() ?: return null
    return best.key.takeIf { best.value >= min && best.value - (s.getOrNull(1)?.value ?: -1f) >= margin }
}

// A report split into short clauses, so "can't move my leg" and "lots of blood" are each matched on their own.
fun clauses(text: String): List<String> =
    text.split(Regex("[.!?;,]+|\\s+(?:at|and|pero|tapos|kasi|but|then|saka)\\s+",RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.length > 2 }

// Model output → one of the given FAQ ids or NO_FAQ, or null if it's anything else (caller uses the dictionary).
fun validateFaq(raw: String, ids: Collection<String>): String? {
    val t = raw.trim().substringBefore('\n').trim(' ', '"', '\'', '.', '`', '*').lowercase()
    return if (t == "none") NO_FAQ else ids.firstOrNull { it.lowercase() == t }
}

// Model output → Facts, or null if anything is off the closed lists (caller falls back to the dictionary).
fun validate(raw: String): Facts? = runCatching {
    val o = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
    fun ids(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it).uppercase() } }.orEmpty()
    val people = o.optInt("people", 0)
    val floor = o.optInt("floor", 0)
    require(people in 0..30 && floor in 0..ROOF)
    Facts(
        hazard = o.optString("hazard", "NONE").uppercase().let { if (it == "NONE") null else Hazard.valueOf(it) },
        people = people,
        trapped = when (o.optString("trapped", "UNKNOWN").uppercase()) {
            "YES" -> true; "NO" -> false; "UNKNOWN" -> null; else -> error("trapped")
        },
        floor = floor,
        spot = Spot.valueOf(o.optString("spot", "UNKNOWN").uppercase()),
        vuln = ids("vulnerable").map { Vuln.valueOf(it) }.toSet(),
        cond = ids("condition").map { Cond.valueOf(it) }.toSet(),
    )
}.getOrNull()

// ---- 22-byte beacon record (PRD §8) ----
// PRD's field sizes add up to 23, so ver and hops share byte 0 (ver high nibble, hops low nibble; hops ≤ 5).
// [0] ver|hops [1-4] id [5-8] lat×1e5 [9-12] lon×1e5 [13] code|tri|trapped|worse [14] people
// [15] vulnerable bits [16] condition bits [17] floor|spot [18] battery % [19] revision [20-21] age in minutes

enum class Code { COLLAPSE, FLOOD, LANDSLIDE, OTHER, SAFE, ACK, RESC, RING }

const val NO_FIX = Int.MIN_VALUE
const val RECORD_VER = 1
const val RECORD_SIZE = 22

data class Record(
    val id: Int, val code: Code, val tri: Tri = Tri.YELLOW, val trapped: Boolean = false, val worse: Boolean = false,
    val people: Int = 0, val vuln: Set<Vuln> = emptySet(), val cond: Set<Cond> = emptySet(),
    val floor: Int = 0, val spot: Spot = Spot.UNKNOWN,
    val lat: Int = NO_FIX, val lon: Int = NO_FIX, val battery: Int = 0, val hops: Int = 0, val rev: Int = 1, val age: Int = 0,
) {
    fun encode(): ByteArray = java.nio.ByteBuffer.allocate(RECORD_SIZE)
        .put((RECORD_VER shl 4 or hops.coerceIn(0, 15)).toByte())
        .putInt(id).putInt(lat).putInt(lon)
        .put((code.ordinal shl 4 or (tri.ordinal shl 2) or ((if (trapped) 1 else 0) shl 1) or (if (worse) 1 else 0)).toByte())
        .put(people.coerceIn(0, 255).toByte())
        .put(bits(vuln).toByte()).put(bits(cond).toByte())
        .put((floor.coerceIn(0, ROOF) shl 4 or spot.ordinal).toByte())
        .put(battery.coerceIn(0, 100).toByte())
        .put(rev.coerceIn(0, 255).toByte())
        .putShort(age.coerceIn(0, 0xFFFF).toShort())
        .array()

    companion object {
        // Null for anything that isn't a valid v1 record (wrong size, version or code).
        fun decode(b: ByteArray): Record? {
            if (b.size != RECORD_SIZE) return null
            val bb = java.nio.ByteBuffer.wrap(b)
            fun u() = bb.get().toInt() and 0xFF
            val vh = u()
            if (vh shr 4 != RECORD_VER) return null
            val id = bb.int; val lat = bb.int; val lon = bb.int
            val kind = u(); val people = u(); val vuln = u(); val cond = u(); val place = u()
            val battery = u(); val rev = u(); val age = bb.short.toInt() and 0xFFFF
            return Record(
                id = id, code = Code.entries.getOrNull(kind shr 4) ?: return null,
                tri = Tri.entries.getOrNull(kind shr 2 and 3) ?: return null,
                trapped = kind and 2 != 0, worse = kind and 1 != 0,
                people = people, vuln = unbits(vuln, Vuln.entries), cond = unbits(cond, Cond.entries),
                floor = place shr 4, spot = Spot.entries.getOrNull(place and 15) ?: return null,
                lat = lat, lon = lon, battery = battery, hops = vh and 15, rev = rev, age = age,
            )
        }
    }
}

private fun <E : Enum<E>> bits(s: Set<E>) = s.fold(0) { acc, e -> acc or (1 shl e.ordinal) }
private fun <E : Enum<E>> unbits(b: Int, all: List<E>) = all.filter { b and (1 shl it.ordinal) != 0 }.toSet()

// Victim facts → the record fields the facts decide. Beacon fills id, rev, location, battery and age.
fun Facts.toRecord(worse: Boolean) = Record(
    id = 0, code = hazard?.let { Code.valueOf(it.name) } ?: Code.OTHER, tri = triage(this),
    trapped = trapped == true, worse = worse, people = people, vuln = vuln, cond = cond, floor = floor, spot = spot,
)

// Dedupe on (id, revision): same or newer revision refreshes the board (battery, age, location); older is a stale copy.
fun supersedes(new: Record, old: Record?) = old == null || (new.id == old.id && new.rev >= old.rev)

// ---- Beacon life (survival mode) ----
// One battery reading: time, charge counter in µAh (≤ 0 if the phone doesn't report it), percent.
data class Sample(val atMs: Long, val uah: Long, val pct: Int)

// Hours left at the drain measured between two readings, or null until there's a real drop over ≥ 5 min.
// The charge counter resolves far finer than 1 %, so it gives a number much sooner when the phone has one.
fun hoursLeft(a: Sample, b: Sample): Double? {
    val h = (b.atMs - a.atMs) / 3.6e6
    if (h < 5.0 / 60) return null
    return when {
        a.uah > 0 && b.uah in 1 until a.uah -> b.uah / ((a.uah - b.uah) / h)
        b.pct in 0 until a.pct -> b.pct / ((a.pct - b.pct) / h)
        else -> null
    }
}

// ---- Trained heads (train/train_heads.py → assets/heads.json) ----
// Logistic regression on the EmbeddingGemma vector: softmax for hazard and trapped, a sigmoid per condition and
// vulnerable group. Each label has its own threshold, picked for high precision on validation data.

class Softmax(val labels: List<String>, val w: List<FloatArray>, val b: FloatArray, val min: Float)
class Sigmoid(val w: FloatArray, val b: Float, val min: Float)

class Heads(val softmax: Map<String, Softmax>, val sigmoid: Map<String, Sigmoid>) {
    // Same heads with every threshold raised to at least its bar (by head or label name).
    fun atLeast(bars: Map<String, Float>) = Heads(
        softmax.mapValues { (k, s) -> Softmax(s.labels, s.w, s.b, maxOf(s.min, bars[k] ?: 0f)) },
        sigmoid.mapValues { (k, s) -> Sigmoid(s.w, s.b, maxOf(s.min, bars[k] ?: 0f)) },
    )

    companion object {
        fun parse(json: String): Heads {
            val o = JSONObject(json)
            fun floats(a: org.json.JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
            val sm = o.getJSONObject("softmax").let { s ->
                s.keys().asSequence().associateWith { k ->
                    val h = s.getJSONObject(k)
                    val w = h.getJSONArray("W")
                    Softmax(h.getJSONArray("labels").let { l -> (0 until l.length()).map(l::getString) },
                        (0 until w.length()).map { floats(w.getJSONArray(it)) }, floats(h.getJSONArray("b")), h.getDouble("min").toFloat())
                }
            }
            val sg = o.getJSONObject("sigmoid").let { s ->
                s.keys().asSequence().associateWith { k ->
                    val h = s.getJSONObject(k)
                    Sigmoid(floats(h.getJSONArray("w")), h.getDouble("b").toFloat(), h.getDouble("min").toFloat())
                }
            }
            return Heads(sm, sg)
        }
    }
}

// Labels the heads are sure of, and every probability (for the demo overlay). vs = the whole report and each
// clause; per label the highest probability over them counts. A softmax head answers its first label (NONE /
// UNKNOWN) unless another label clears the threshold.
data class HeadOut(val picks: Map<String, String>, val labels: Set<String>, val p: Map<String, Float>)

fun applyHeads(h: Heads, vs: List<FloatArray>): HeadOut {
    val p = HashMap<String, Float>()
    val picks = h.softmax.mapValues { (name, s) ->
        val best = FloatArray(s.labels.size)
        vs.forEach { v ->
            val z = FloatArray(s.labels.size) { dot(s.w[it], v) + s.b[it] }
            val m = z.max()
            val e = z.map { Math.exp((it - m).toDouble()) }
            val sum = e.sum()
            e.forEachIndexed { i, x -> best[i] = maxOf(best[i], (x / sum).toFloat()) }
        }
        s.labels.forEachIndexed { i, l -> p["$name.$l"] = best[i] }
        (1 until s.labels.size).maxByOrNull { best[it] }?.takeIf { best[it] >= s.min }?.let { s.labels[it] } ?: s.labels[0]
    }
    val labels = h.sigmoid.filter { (l, s) ->
        vs.maxOf { v -> (1 / (1 + Math.exp(-(dot(s.w, v) + s.b).toDouble()))).toFloat() }.also { p[l] = it } >= s.min
    }.keys
    return HeadOut(picks, labels, p)
}
