package ph.saklolo

// Keyword baseline (frozen, the Eval comparison), rules for exact facts, and the fallback when the model can't run.
// ponytail: baseline() is plain keyword hits by design; no negation, corrections or place
// What a spoken line in the chat is for; decided by keywords so it's instant and works without the model.
enum class Route { NEXT, REPEAT, ANSWER, QUESTION, REPORT }

object Dictionary {
    private val hazards = listOf(
        Hazard.COLLAPSE to listOf("gumuho", "collapse", "lindol", "earthquake"),
        Hazard.FLOOD to listOf("baha", "flood"),
        Hazard.LANDSLIDE to listOf("landslide", "guho ng lupa"),
    )
    private val conds = listOf(
        Cond.NOT_BREATHING to listOf("hindi humihinga", "not breathing"),
        Cond.UNCONSCIOUS to listOf("walang malay", "unconscious"),
        Cond.HEAVY_BLEEDING to listOf("dumudugo", "bleeding"),
        Cond.INJURED to listOf("sugatan", "injured"),
        Cond.WATER_RISING to listOf("tumataas ang tubig", "water is rising", "water rising"),
    )
    private val vulns = listOf(
        Vuln.INFANT to listOf("sanggol", "baby"),
        Vuln.CHILD to listOf("bata", "child"),
        Vuln.ELDERLY to listOf("lola", "lolo", "elderly"),
        Vuln.PREGNANT to listOf("buntis", "pregnant"),
    )
    private val numbers = listOf(
        listOf("isa", "one"), listOf("dalawa", "two"), listOf("tatlo", "three"), listOf("apat", "four"),
        listOf("lima", "five"), listOf("anim", "six"), listOf("pito", "seven"), listOf("walo", "eight"),
    )

    // Hedges are checked first, so "hindi ko alam" and "oo yata" are UNSURE, not NO or YES. Nothing heard → UNSURE.
    fun answer(text: String): Ans {
        val s = " " + text.lowercase().replace(Regex("[^a-z]+"), " ") + " "
        fun any(vararg ws: String) = ws.any { " $it " in s }
        return when {
            any("hindi ko alam", "di ko alam", "ewan", "yata", "siguro", "baka", "parang", "medyo", "hindi sigurado",
                "maybe", "not sure", "i think", "don t know", "dont know", "probably") -> Ans.UNSURE
            any("hindi", "hinde", "di", "wala", "ayaw", "no", "nope", "not", "can t", "cannot", "cant") -> Ans.NO
            any("oo", "opo", "oho", "kaya", "pwede", "puwede", "yes", "yeah", "yep") -> Ans.YES
            else -> Ans.UNSURE
        }
    }

    // Shared words with an approved question's examples. ≥ 2 and a clear winner, else NONE (a wrong answer is worse).
    private val stop = setOf("should", "could", "would", "what", "when", "where", "with", "this", "that", "there", "their", "they",
        "pwede", "puwede", "dapat", "kung", "para", "naman", "lang", "siya", "sila", "kami", "kayo", "will", "have", "from", "your")
    private fun words(s: String) = s.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 4 && it !in stop }.toSet()

    fun matchFaq(question: String, faqs: List<Faq>): String {
        val q = words(question)
        val scored = faqs.map { f -> f.id to f.examples.maxOf { (words(it) intersect q).size } }.sortedByDescending { it.second }
        val (best, second) = scored.getOrNull(0) to scored.getOrNull(1)
        return if (best != null && best.second >= 2 && best.second > (second?.second ?: 0)) best.first else NO_FAQ
    }

    // Frozen keyword baseline: never improve it, it's what the AI is compared against.
    fun baseline(text: String): Facts {
        val s = text.lowercase()
        fun <T> hits(table: List<Pair<T, List<String>>>) = table.filter { (_, ws) -> ws.any { it in s } }.map { it.first }
        // First number heard wins ("tatlo ... ay hindi, apat" → 3).
        val people = Regex("[a-z0-9]+").findAll(s).firstNotNullOfOrNull { m ->
            m.value.toIntOrNull()?.takeIf { it in 1..30 } ?: numbers.indexOfFirst { m.value in it }.takeIf { it >= 0 }?.plus(1)
        } ?: 0
        return Facts(
            hazard = hits(hazards).firstOrNull(),
            people = people,
            trapped = if (listOf("naipit", "trapped", "stuck").any { it in s }) true else null,
            vuln = hits(vulns).toSet(),
            cond = hits(conds).toSet(),
        )
    }

    // ---- Rules: the exact facts meaning can't give (headcount, floor, room), plus the no-model fallback ----
    // ponytail: hand-written word lists; extend them with misses from the Eval screen, never tune on held-out reports

    private val num = mapOf(
        "isa" to 1, "one" to 1, "dalawa" to 2, "dalawang" to 2, "two" to 2, "tatlo" to 3, "tatlong" to 3, "three" to 3,
        "apat" to 4, "four" to 4, "lima" to 5, "limang" to 5, "five" to 5, "anim" to 6, "six" to 6,
        "pito" to 7, "pitong" to 7, "seven" to 7, "walo" to 8, "walong" to 8, "eight" to 8, "siyam" to 9, "nine" to 9,
        "sampu" to 10, "sampung" to 10, "ten" to 10,
    )
    private val ordinal = mapOf(
        "una" to 1, "unang" to 1, "first" to 1, "1st" to 1, "ground" to 1, "ikalawa" to 2, "ikalawang" to 2, "pangalawang" to 2,
        "second" to 2, "2nd" to 2, "ikatlo" to 3, "ikatlong" to 3, "pangatlong" to 3, "third" to 3, "3rd" to 3,
        "ikaapat" to 4, "ika-apat" to 4, "ikaapat na" to 4, "fourth" to 4, "4th" to 4, "ikalima" to 5, "ikalimang" to 5, "fifth" to 5, "5th" to 5,
    )
    private val floorWord = "(?:palapag|floor|flr|storey|story)"
    private val roofWords = listOf("bubong", "roof", "rooftop", "atop")
    private val companions = listOf("wife", "husband", "asawa", "nanay", "tatay", "mother", "father", "mom", "dad", "lola", "lolo",
        "grandma", "grandmother", "grandpa", "grandfather", "son", "daughter", "kapatid", "brother", "sister", "friend", "kaibigan", "baby", "sanggol")
    private val corrections = listOf("ay hindi", "hindi pala", "pala", "no wait", "no,", "i mean", "actually", "mali,", "este")

    private val spots = listOf(
        Spot.UNDER_TABLE to listOf("ilalim ng mesa", "ilalim ng lamesa", "under the table", "under the dining table", "under a table", "sa ilalim ng mesa"),
        Spot.BATHROOM to listOf("banyo", "cr", "comfort room", "bathroom", "toilet", "kubeta", "palikuran"),
        Spot.STAIRS to listOf("hagdan", "hagdanan", "stairs", "staircase", "stairway"),
        Spot.KITCHEN to listOf("kusina", "kitchen"),
        Spot.HALLWAY to listOf("pasilyo", "hallway", "corridor"),
        Spot.ATTIC to listOf("attic", "kisame", "ceiling space"),
        Spot.ROOM to listOf("kwarto", "kuwarto", "silid", "bedroom", "room", "sala", "living room"),
        Spot.OUTSIDE to listOf("labas ng bahay", "outside", "sa kalsada", "street"),
    )
    private val ruleHazards = listOf(
        Hazard.LANDSLIDE to listOf("landslide", "guho ng lupa", "pagguho ng lupa", "natabunan ng lupa", "mudslide"),
        Hazard.COLLAPSE to listOf("gumuho", "guho", "collapse", "lindol", "lumindol", "earthquake", "bumagsak ang kisame", "ceiling came down", "nadaganan", "aftershock", "rubble", "debris"),
        Hazard.FLOOD to listOf("baha", "flood", "tubig", "storm surge", "daluyong", "water"),
    )
    private val ruleConds = listOf(
        Cond.NOT_BREATHING to listOf("hindi humihinga", "hindi na humihinga", "di humihinga", "not breathing", "stopped breathing", "walang hininga", "no breathing"),
        Cond.UNCONSCIOUS to listOf("walang malay", "nawalan ng malay", "unconscious", "passed out", "hindi magising", "won't wake up"),
        Cond.HEAVY_BLEEDING to listOf("dumudugo", "bleeding", "daming dugo", "maraming dugo", "lots of blood", "so much blood", "a lot of blood", "nagdurugo"),
        Cond.PINNED to listOf("hindi ko maigalaw", "hindi maigalaw", "can't move my", "cannot move my", "pinned", "nadaganan", "naipit ang paa", "naipit ang binti", "naipit ang kamay", "naipit ang braso", "under the beam"),
        Cond.INJURED to listOf("sugatan", "injured", "broken", "bali", "nabalian", "sugat", "is cut", "hiwa", "hurt", "masakit"),
        Cond.WATER_RISING to listOf("tumataas ang tubig", "tumataas pa", "water is rising", "water rising", "water keeps rising", "rising water", "umaakyat na yung tubig", "umaakyat ang tubig", "lumalalim",
            "filling with water", "filling up with water", "pumapasok ang tubig", "pumapasok na ang tubig", "lumulubog", "tumataas ang baha"),
    )
    private val ruleVulns = listOf(
        Vuln.INFANT to listOf("sanggol", "baby", "infant", "newborn"),
        Vuln.CHILD to listOf("bata", "child", "kid", "kids", "anak ko", "my son", "my daughter", "pamangkin"),
        Vuln.ELDERLY to listOf("lola", "lolo", "matanda", "elderly", "grandma", "grandmother", "grandpa", "grandfather", "senior"),
        Vuln.PREGNANT to listOf("buntis", "pregnant"),
        Vuln.PWD to listOf("pwd", "wheelchair", "naka-wheelchair", "bulag", "blind", "bingi", "deaf", "disabled", "lumpo"),
    )
    private val notTrapped = listOf("hindi naiipit", "hindi naman naiipit", "hindi kami naiipit", "hindi naman kami naiipit", "di naiipit",
        "nakalabas", "got out", "not trapped", "we're out", "we are out", "ligtas na kami", "we're safe")
    private val trappedWords = listOf("naipit", "naiipit", "trapped", "stuck", "hindi makalabas", "di makalabas", "can't get out", "cannot get out",
        "can't get down", "hindi makababa", "hindi na kami makababa", "nakulong", "can't open the door", "hindi mabuksan", "pinned", "came down on us", "natabunan",
        "trap", "na-trap", "napapalibutan", "napalibutan", "surrounded", "stranded", "can't get out", "cannot get out")
    // "hindi ako makalabas", "di kami makababa": the negation and the verb with up to two words between.
    private val leaveVerbs = listOf("makalabas", "makababa", "makaalis", "makaakyat", "makatakas", "mabuksan")
    private fun cantLeave(s: String): Boolean {
        val w = s.trim().split(" ")
        return w.indices.any { i -> w[i] in setOf("hindi", "di") && (i + 1..minOf(i + 3, w.lastIndex)).any { j -> leaveVerbs.any { near(w[j], it) } } }
    }
    // "nasa ilalim ako ng rubble": under rubble, debris or a wall means trapped, pinned and a collapse.
    private val underRubble = Regex(" (?:ilalim|under) (?:\\S+ ){0,2}?(?:ng |the |of )?(?:rubble|ruble|debris|guho|semento|pader|concrete|wall|bato) ")
    // A person describing their own breathing is breathing: never CPR.
    private val breathingWords = listOf("trouble breathing", "hard to breathe", "can't breathe", "cannot breathe", "difficulty breathing",
        "short of breath", "hirap huminga", "hirap akong huminga", "nahihirapan huminga", "nahihirapan akong huminga", "hindi makahinga",
        "di makahinga", "hindi ako makahinga", "sumisikip ang dibdib", "masikip ang dibdib")
    fun breathingTrouble(text: String) = norm(text).let { s -> breathingWords.any { has(s, it) } }

    // Lowercase, punctuation as spaces, padded so " word " matches whole words and phrases.
    // Common Filipino text-speak, expanded before matching ("hindi p kmi makalbas" → "hindi pa kami makalbas").
    private val textSpeak = mapOf(
        "kmi" to "kami", "nmin" to "namin", "nsa" to "nasa", "p" to "pa", "n" to "na", "q" to "ko", "aq" to "ako", "aku" to "ako",
        "hnd" to "hindi", "hndi" to "hindi", "indi" to "hindi", "d2" to "dito", "dto" to "dito", "ung" to "yung", "yng" to "yung",
        "lng" to "lang", "nmn" to "naman", "sya" to "siya", "wla" to "wala", "tlga" to "talaga", "pls" to "please", "plz" to "please",
        "bkt" to "bakit", "pno" to "paano", "pde" to "pwede", "pwd" to "pwede",
    )
    private fun norm(t: String) = " " + t.lowercase().replace(Regex("[^a-z0-9'\\-]+"), " ").trim()
        .split(" ").joinToString(" ") { textSpeak[it] ?: it } + " "

    // Same word give or take one letter (typed in a hurry: "makalbas", "earthqake"). Long words only, so short
    // ones that differ by a letter ("baha"/"bahay", "bubog"/"bubong") never match.
    // ponytail: one-edit typo tolerance, not a spellchecker; add common misspellings to the word lists instead
    private fun near(a: String, b: String): Boolean {
        if (a == b) return true
        // Same first two letters too: Tagalog changes meaning up front ("makalabas" can get out, "nakalabas" got out).
        if (a.length < 6 || b.length < 6 || kotlin.math.abs(a.length - b.length) > 1 || a.take(2) != b.take(2)) return false
        var i = 0; var j = 0; var d = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++d > 1) return false
            when { a.length > b.length -> i++; a.length < b.length -> j++; else -> { i++; j++ } }
        }
        return d + (a.length - i) + (b.length - j) <= 1
    }

    private fun has(s: String, w: String): Boolean {
        val p = norm(w).trim()
        if (" $p " in s) return true
        if (' ' !in p && s.trim().split(" ").any { near(it, p) }) return true
        val last = p.substringAfterLast(' ')
        if (last.length < 4) return false
        val c = Regex.escape(last.last().toString())
        return Regex(" ${Regex.escape(p)}(?:s|es|ed|d|ing|ren|${c}ed|${c}ing) ").containsMatchIn(s)
    }
    private fun <T> hits(s: String, table: List<Pair<T, List<String>>>) = table.filter { (_, ws) -> ws.any { has(s, it) } }.map { it.first }

    fun floorOf(text: String): Int {
        val s = norm(text)
        Regex("(?:(\\d+)(?:st|nd|rd|th)?\\s+$floorWord|$floorWord\\s+(\\d+))").find(s)?.let { m ->
            return (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt().coerceIn(1, 14)
        }
        ordinal.entries.sortedByDescending { it.key.length }.firstOrNull { (w, _) -> Regex(" $w (?:na )?$floorWord ").containsMatchIn(s) }?.let { return it.value }
        if (has(s, "ground floor")) return 1
        return if (roofWords.any { has(s, it) }) ROOF else 0
    }

    // Headcount: number words that aren't floors; after a correction ("ay hindi", "no wait") the later number wins.
    fun peopleOf(text: String): Int {
        var s = norm(text)
        s = s.replace(Regex(" \\S+ (?:na )?$floorWord "), " ") // drop "ikalawang palapag", "2nd floor"
        val found = Regex("[a-z0-9]+").findAll(s).mapNotNull { m -> (m.value.toIntOrNull()?.takeIf { it in 1..30 } ?: num[m.value])?.let { it to m.range.first } }.toList()
        val singular = listOf("ako", "akong", "i'm", "im", "i am", "i", "me").any { has(s, it) }
        val me = singular && listOf("kami", "kaming", "tayo", "we", "we're", "us").none { has(s, it) }
        val with = listOf("with", "kasama", "kasabay").any { has(s, it) }
        val others = if (with) companions.count { has(s, it) } else 0
        if (found.isEmpty()) return when {
            listOf("mag-isa", "ako lang", "alone", "by myself", "only me", "just me").any { has(s, it) } -> 1
            me || (singular && with) -> 1 + others
            else -> 0
        }
        if (singular && with) return 1 + others + found.sumOf { it.first } // "I'm with my wife and two children"
        val corrected = found.size > 1 && corrections.any { c -> s.indexOf(" ${norm(c).trim()} ").let { it > found.first().second } }
        return if (corrected) found.last().first else found.first().first
    }

    fun spotOf(text: String): Spot = hits(norm(text), spots).firstOrNull() ?: Spot.UNKNOWN

    fun trappedOf(text: String): Boolean? = norm(text).let { s ->
        when { notTrapped.any { has(s, it) } -> false; trappedWords.any { has(s, it) } || cantLeave(s) || underRubble.containsMatchIn(s) -> true; else -> null }
    }

    private val noInjury = listOf("walang sugatan", "wala namang sugatan", "walang nasaktan", "no one is hurt", "nobody is hurt", "no one is injured", "nobody is injured", "not hurt", "hindi nasaktan")

    // Rules-only reading of a report: the fallback without the model, and the exact fields alongside it.
    fun extract(text: String): Facts {
        var s = norm(text)
        noInjury.forEach { s = s.replace(" ${norm(it).trim()} ", " ") }
        val rubble = underRubble.containsMatchIn(s)
        val cond = hits(s, ruleConds).toSet() + (if (rubble) setOf(Cond.PINNED) else emptySet()) +
            (if (breathingTrouble(text)) setOf(Cond.INJURED) else emptySet())
        return Facts(
            hazard = hits(s, ruleHazards).firstOrNull() ?: Hazard.COLLAPSE.takeIf { rubble }, people = peopleOf(text), trapped = trappedOf(text),
            floor = floorOf(text), spot = spotOf(text), vuln = hits(s, ruleVulns).toSet(), cond = cond,
        )
    }

    // ---- Hands-free mic ----
    // Whisper writes these when it hears only noise (rain, rubble, breathing). An always-open mic must never pass them
    // on as a report. Bracketed tags ("[Music]", "(wind)") are dropped first; a line with nothing left is noise too.
    private val hallucinations = setOf(
        "you", "thank you", "thanks", "thank you very much", "thanks for watching", "thank you for watching",
        "please subscribe", "subscribe", "bye", "bye bye", "salamat sa panonood", "salamat po sa panonood",
    )
    fun noise(said: String): Boolean {
        val s = norm(said.replace(Regex("\\[[^]]*]|\\([^)]*\\)|\\*[^*]*\\*"), " ")).trim()
        return s.none { it.isLetter() } || s in hallucinations
    }

    // ---- Chat routing ----
    private val askWords = listOf("ba", "bang", "can i", "can we", "should i", "should we", "is it", "what if", "paano", "bakit", "how do", "why")
    private val nextWords = listOf("susunod", "next", "sige", "okay", "ok", "tapos na", "done", "tuloy", "ready", "go on")
    private val repeatWords = listOf("ulit", "ulitin", "pakiulit", "repeat", "again", "ano", "what", "huh", "sorry")

    // A short line while a yes/no question waits is the answer, unless it brings new facts (those always count).
    // Questions go to the approved answers; "next"/"repeat" move the steps; anything else is new information.
    fun route(said: String, yesNoWaiting: Boolean): Route {
        val s = norm(said)
        val words = s.trim().split(" ").size
        val facts = extract(said).let { it.hazard != null || it.cond.isNotEmpty() }
        return when {
            said.trim().endsWith("?") || askWords.any { has(s, it) } -> Route.QUESTION
            yesNoWaiting && words <= 6 && !facts -> Route.ANSWER
            words <= 3 && nextWords.any { has(s, it) } -> Route.NEXT
            words <= 3 && repeatWords.any { has(s, it) } -> Route.REPEAT
            else -> Route.REPORT
        }
    }
}
