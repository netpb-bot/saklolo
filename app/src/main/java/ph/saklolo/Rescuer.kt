package ph.saklolo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Rescuer side, pure: ranking, GPS bearing, smoothed RSSI, spoken briefing. No AI here (PRD D9).

// One record as heard: raw RSSI samples (elapsedRealtime ms, dBm) from the last SAMPLE_MS, and when we last heard it.
data class Heard(val rec: Record, val samples: List<Pair<Long, Int>>, val at: Long)

const val SAMPLE_MS = 20_000L

private val notVictims = setOf(Code.SAFE, Code.ACK, Code.RESC, Code.RING)

// When the report started: heard time minus the age the victim phone put in the record.
fun reportedAt(h: Heard) = h.at - h.rec.age * 60_000L

// RED > YELLOW > GREEN, then any vulnerable person, then oldest report first. Silent records keep their rank.
fun rank(all: Collection<Heard>): List<Heard> = all.filter { it.rec.code !in notVictims }.sortedWith(
    compareByDescending<Heard> { it.rec.tri.ordinal }.thenByDescending { it.rec.vuln.isNotEmpty() }.thenBy { reportedAt(it) },
)

fun safeCount(all: Collection<Heard>) = all.count { it.rec.code == Code.SAFE }

const val SILENT_MS = 2 * 60_000L // continuous beacons repeat every second; low-battery ones every 60 s
const val LOW_BATTERY = 15

// ---- Hot/cold ----
// ponytail: fixed dBm thresholds; calibrate on the demo phones in the hall (walls and bodies shift these a lot)
val BAND_DBM = intArrayOf(-80, -70, -60) // Cold < -80 ≤ Warm < -70 ≤ Hot < -60 ≤ Right here

private fun median(xs: List<Double>): Double? = xs.sorted().takeIf { it.isNotEmpty() }?.let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }

private fun recent(samples: List<Pair<Long, Int>>, now: Long) = samples.filter { now - it.first <= 3_000 }.map { it.second.toDouble() }

// Median of the last 3 s (multipath spikes of ±10 dB are common), else of the last 5 samples heard.
fun smooth(samples: List<Pair<Long, Int>>, now: Long): Double? =
    median(recent(samples, now).ifEmpty { samples.takeLast(5).map { it.second.toDouble() } })

// 0 Cold, 1 Warm, 2 Hot, 3 Right here.
fun band(dbm: Double) = BAND_DBM.count { dbm >= it }

// 0..1 for the meter and the beep rate.
fun level(dbm: Double) = ((dbm + 95) / 50).coerceIn(0.0, 1.0)

// ponytail: log-distance path loss; ref1m (calibrated per hall) and the exponent 2.5 are the knobs. Rough to ±50%.
const val REF_1M = -59.0
fun approxMeters(dbm: Double, ref1m: Double = REF_1M) = 10.0.pow((ref1m - dbm) / 25)

fun distanceBucket(m: Double) = when {
    m < 2 -> "< 2 m"
    m < 7 -> "~5 m"
    m < 15 -> "~10 m"
    else -> "20 m+"
}

// ---- Live compass ----
// A phone can't sense a BLE signal's direction, but the rescuer's body blocks 5–15 dB from behind. So every sample is
// paired with where the phone faced, as dB above the 3 s median at that moment (walking closer then doesn't read as
// a direction). Rescuers looking around rubble fill the circle without stopping to sweep.
const val LIVE_MS = 12_000L

// 12 sectors of 30°, median per sector. Null until half the circle is seen with a clear strongest side.
// ponytail: 6 sectors and 4 dB are the hall-tuning knobs (fewer = faster needle, more = fewer wrong ones).
fun bestHeading(pts: List<Pair<Float, Double>>): Float? {
    val sectors = pts.groupBy { ((it.first % 360 + 360) % 360 / 30).toInt() % 12 }.mapValues { (_, v) -> median(v.map { it.second })!! }
    if (sectors.size < 6) return null
    val best = sectors.maxBy { it.value }
    if (best.value - sectors.values.min() < 4) return null
    return best.key * 30f + 15f
}

// Target relative to where the phone faces: -180..180, positive = to the right.
fun relativeDeg(target: Float, heading: Float) = ((target - heading) % 360 + 540) % 360 - 180

// ---- Auto-ring ----
// Rubble can cost 10–20 dB, so the threshold is generous: ringing early is cheap, and the sound is what finds them.
const val AUTO_RING_M = 8.0
const val AUTO_RING_EVERY_MS = 40_000L // the victim rings for 30 s; re-ring while the rescuer stays close

// Most of the last 3 s within AUTO_RING_M (the median ignores a lone spike), from at least 3 samples.
fun closeEnough(samples: List<Pair<Long, Int>>, now: Long, ref1m: Double = REF_1M): Boolean {
    val r = recent(samples, now)
    return r.size >= 3 && approxMeters(median(r)!!, ref1m) <= AUTO_RING_M
}

// ---- GPS (lat/lon are ×1e5 ints, NO_FIX = unknown) ----
private fun rad(e5: Int) = Math.toRadians(e5 / 1e5)

fun distanceM(lat1: Int, lon1: Int, lat2: Int, lon2: Int): Double {
    val dLat = rad(lat2) - rad(lat1)
    val dLon = rad(lon2) - rad(lon1)
    val a = sin(dLat / 2).let { it * it } + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2).let { it * it }
    return 6_371_000 * 2 * atan2(sqrt(a), sqrt(1 - a))
}

// Initial bearing from 1 to 2, degrees clockwise from north.
fun bearingDeg(lat1: Int, lon1: Int, lat2: Int, lon2: Int): Double {
    val dLon = rad(lon2) - rad(lon1)
    val y = sin(dLon) * cos(rad(lat2))
    val x = cos(rad(lat1)) * sin(rad(lat2)) - sin(rad(lat1)) * cos(rad(lat2)) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

fun compass8(deg: Double) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((deg + 22.5) / 45).toInt() % 8]

fun distanceText(m: Double) = if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(m / 1000)

// ---- Spoken briefing (a template, never the model). English default, Filipino on the toggle. ----
fun briefing(r: Record, minutesAgo: Long, fil: Boolean): String {
    fun t(en: String, f: String) = if (fil) f else en
    val tri = when (r.tri) { Tri.RED -> t("Red", "Pula"); Tri.YELLOW -> t("Yellow", "Dilaw"); Tri.GREEN -> t("Green", "Berde") }
    val what = when (r.code) {
        Code.COLLAPSE -> t("Collapse", "Gumuhong gusali")
        Code.FLOOD -> t("Flood", "Baha")
        Code.LANDSLIDE -> t("Landslide", "Pagguho ng lupa")
        else -> t("Emergency", "Emergency")
    }
    val people = when (r.people) {
        0 -> t("Number of people unknown", "Hindi alam kung ilan")
        1 -> t("One person", "Isang tao")
        else -> t("${r.people} people", "${r.people} katao")
    } + (if (r.trapped) t(", trapped", ", naiipit") else "")
    val vuln = r.vuln.sorted().map {
        when (it) {
            Vuln.INFANT -> t("a baby", "sanggol")
            Vuln.CHILD -> t("a child", "bata")
            Vuln.ELDERLY -> t("an elderly person", "matanda")
            Vuln.PREGNANT -> t("a pregnant woman", "buntis")
            Vuln.PWD -> t("a person with disability", "PWD")
        }
    }
    val including = if (vuln.isEmpty()) "" else t(", including ", ", kasama ang ") + andList(vuln, t(" and ", " at "))
    val place = listOfNotNull(
        when (r.floor) { 0 -> null; ROOF -> t("On the roof", "Nasa bubong"); else -> t("${ordinal(r.floor)} floor", "Palapag ${r.floor}") },
        spotWord(r.spot, fil),
    ).joinToString(", ")
    val conds = r.cond.sorted().map { // enum order = most urgent first
        when (it) {
            Cond.NOT_BREATHING -> t("Someone is not breathing", "May hindi humihinga")
            Cond.UNCONSCIOUS -> t("Someone is unconscious", "May walang malay")
            Cond.HEAVY_BLEEDING -> t("Heavy bleeding", "Malakas na pagdurugo")
            Cond.PINNED -> t("Someone is pinned", "May naipit ang katawan")
            Cond.INJURED -> t("Injured", "May sugatan")
            Cond.WATER_RISING -> t("Water is rising", "Tumataas ang tubig")
        }
    }
    return listOf(
        tri, what, people + including, place, *conds.toTypedArray(),
        if (r.worse) t("Getting worse", "Lumalala") else "",
        if (r.lat == NO_FIX) t("No GPS", "Walang GPS") else "",
        t("Battery ${r.battery} percent", "Baterya ${r.battery} porsiyento"),
        t("Reported $minutesAgo minutes ago", "Iniulat $minutesAgo minuto na ang nakalipas"),
    ).filter { it.isNotEmpty() }.joinToString(". ", postfix = ".")
}

fun spotWord(s: Spot, fil: Boolean): String? = when (s) {
    Spot.UNKNOWN -> null
    Spot.BATHROOM -> if (fil) "banyo" else "bathroom"
    Spot.STAIRS -> if (fil) "hagdan" else "stairs"
    Spot.UNDER_TABLE -> if (fil) "ilalim ng mesa" else "under a table"
    Spot.ROOM -> if (fil) "kuwarto" else "room"
    Spot.KITCHEN -> if (fil) "kusina" else "kitchen"
    Spot.HALLWAY -> if (fil) "pasilyo" else "hallway"
    Spot.ATTIC -> if (fil) "kisame" else "attic"
    Spot.OUTSIDE -> if (fil) "labas" else "outside"
}

private fun andList(xs: List<String>, and: String) = if (xs.size == 1) xs[0] else xs.dropLast(1).joinToString(", ") + and + xs.last()

private fun ordinal(n: Int) = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"
    else -> "th"
}
