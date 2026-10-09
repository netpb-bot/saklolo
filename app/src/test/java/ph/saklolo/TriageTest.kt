package ph.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageTest {
    private val demo = "Tulong! Gumuho ang bahay, naipit kami sa ikalawang palapag, sa may banyo. Tatlo kami, ay hindi, apat pala, " +
        "kasama si lola. Hindi ko maigalaw ang binti ko, at ang daming dugo sa ulo ng anak ko!"

    @Test fun triageRule() {
        assertEquals(Tri.YELLOW, triage(Facts())) // bare SOS
        assertEquals(Tri.RED, triage(Facts(cond = setOf(Cond.HEAVY_BLEEDING))))
        assertEquals(Tri.RED, triage(Facts(trapped = true, cond = setOf(Cond.WATER_RISING))))
        assertEquals(Tri.YELLOW, triage(Facts(cond = setOf(Cond.WATER_RISING))))
        assertEquals(Tri.YELLOW, triage(Facts(trapped = true)))
        assertEquals(Tri.YELLOW, triage(Facts(hazard = Hazard.OTHER)))
        assertEquals(Tri.GREEN, triage(Facts(people = 2, trapped = false)))
    }

    @Test fun gettingWorse() {
        val bare = Facts()
        assertFalse(worse(bare, Facts(Hazard.COLLAPSE, 3, true, cond = setOf(Cond.PINNED)))) // unknown → known
        val known = Facts(Hazard.FLOOD, 3, false)
        assertFalse(worse(known, known.merge(Facts(floor = ROOF))))
        assertTrue(worse(known, known.merge(Facts(cond = setOf(Cond.WATER_RISING)))))
        assertTrue(worse(known, known.merge(Facts(people = 4))))
        assertTrue(worse(known, known.merge(Facts(trapped = true))))
        assertFalse(worse(known, known.copy(people = 2)))
    }

    @Test fun followUpSelection() {
        assertEquals(listOf("what", "hurt", "exit", "count"), followUps(Facts()))
        assertEquals(listOf("hurt", "exit", "count", "place"), followUps(Facts(Hazard.COLLAPSE)))
        assertEquals(listOf("hurt", "exit", "place"), followUps(Facts(Hazard.COLLAPSE, 4)))
        // Rising water says nothing about injuries; a flood needs the floor or roof, not the spot.
        assertEquals(listOf("hurt", "exit", "place"), followUps(Facts(Hazard.FLOOD, 4, cond = setOf(Cond.WATER_RISING))))
        assertEquals(listOf("exit"), followUps(Facts(Hazard.FLOOD, 4, floor = ROOF, cond = setOf(Cond.INJURED))))
        assertEquals(listOf("place"), followUps(Facts(Hazard.LANDSLIDE, 2, true, cond = setOf(Cond.PINNED))))
        assertEquals(emptyList<String>(), followUps(Facts(Hazard.OTHER, 2, false, cond = setOf(Cond.INJURED))))
        assertEquals(listOf("hurt"), followUps(Facts(Hazard.COLLAPSE, 4, true, spot = Spot.BATHROOM)))
        assertEquals(emptyList<String>(), followUps(Facts(Hazard.COLLAPSE, 4, true, spot = Spot.BATHROOM, cond = setOf(Cond.INJURED))))
    }

    @Test fun validator() {
        val ok = """Sure: {"hazard":"COLLAPSE","people":4,"trapped":"YES","floor":2,"spot":"BATHROOM",
            "vulnerable":["ELDERLY","CHILD"],"condition":["PINNED","HEAVY_BLEEDING"]}"""
        assertEquals(
            Facts(Hazard.COLLAPSE, 4, true, 2, Spot.BATHROOM, setOf(Vuln.ELDERLY, Vuln.CHILD), setOf(Cond.PINNED, Cond.HEAVY_BLEEDING)),
            validate(ok),
        )
        assertEquals(Facts(), validate("""{"hazard":"NONE"}"""))
        assertNull(validate(ok.replace("BATHROOM", "garage")))
        assertNull(validate(ok.replace("HEAVY_BLEEDING", "BROKEN_ARM")))
        assertNull(validate(ok.replace("\"YES\"", "\"MAYBE\"")))
        assertNull(validate(ok.replace("\"floor\":2", "\"floor\":20")))
        assertNull(validate("I'm sorry, I can't help with that."))
    }

    @Test fun answers() {
        assertEquals(Ans.NO, validateAnswer("NO"))
        assertEquals(Ans.UNSURE, validateAnswer(" unsure.\n"))
        assertNull(validateAnswer("NOT SURE")) // "NOT" must not read as NO
        assertNull(validateAnswer("YES or NO"))
        assertNull(validateAnswer("maybe"))

        assertEquals(Ans.UNSURE, combine(Ans.YES, Ans.NO)) // a clear no is never read as yes
        assertEquals(Ans.NO, combine(null, Ans.NO))
        assertEquals(Ans.UNSURE, combine(Ans.UNSURE, Ans.YES))
        assertEquals(Ans.UNSURE, combine(Ans.YES, Ans.UNSURE)) // a hedge the model reads as yes stays unsure
        assertEquals(Ans.UNSURE, combine(Ans.NO, Ans.UNSURE))
        assertEquals(Ans.YES, combine(Ans.YES, Ans.YES))

        assertEquals(true, Facts().withExit(Ans.UNSURE).trapped) // unclear "Can you get out?" = trapped
        assertEquals(true, Facts().withExit(Ans.NO).trapped)
        assertEquals(false, Facts().withExit(Ans.YES).trapped)

        mapOf(
            "hindi ko alam" to Ans.UNSURE, "oo yata" to Ans.UNSURE, "I think so" to Ans.UNSURE,
            "parang humihinga pero mahina" to Ans.UNSURE, "hindi" to Ans.NO, "No, we can't" to Ans.NO,
            "opo" to Ans.YES, "yes" to Ans.YES, "uhh" to Ans.UNSURE,
        ).forEach { (said, want) -> assertEquals(said, want, Dictionary.answer(said)) }
    }

    @Test fun dictionaryIsTheWeakBaseline() {
        val d = Dictionary.baseline(demo)
        assertEquals(Hazard.COLLAPSE, d.hazard)
        assertEquals(3, d.people) // misses the correction to 4
        assertFalse(Cond.HEAVY_BLEEDING in d.cond)
        assertEquals(Spot.UNKNOWN, d.spot)
    }

    @Test fun rulesReadExactFacts() {
        val r = Dictionary.extract(demo)
        assertEquals(4, r.people) // "tatlo kami, ay hindi, apat pala"
        assertEquals(2, r.floor) // "ikalawang palapag"
        assertEquals(Spot.BATHROOM, r.spot)
        assertEquals(true, r.trapped)
        assertEquals(6, Dictionary.peopleOf("There are five of us, no, six, on the 2nd floor"))
        assertEquals(1, Dictionary.peopleOf("I'm alone on the third floor"))
        assertEquals(3, Dictionary.floorOf("I'm alone on the third floor"))
        assertEquals(ROOF, Dictionary.floorOf("nasa bubong kami"))
        assertEquals(false, Dictionary.trappedOf("Baha dito, hindi naman kami naiipit"))
        assertFalse(Cond.INJURED in Dictionary.extract("Wala namang sugatan, nakalabas na kami").cond)
    }

    // Kotlin tokenizer == official Python sentencepiece. The model file is Gemma-licensed and not in the repo:
    // set SPM_MODEL and SPM_REF (from spm_ref.py) to run it; skipped otherwise.
    @Test fun tokenizerMatchesSentencepiece() {
        val model = System.getenv("SPM_MODEL"); val ref = System.getenv("SPM_REF")
        org.junit.Assume.assumeTrue(model != null && ref != null)
        val spm = Spm(java.io.File(model!!).readBytes())
        val r = org.json.JSONObject(java.io.File(ref!!).readText(Charsets.UTF_8))
        assertEquals(listOf(r.getInt("bos"), r.getInt("eos"), r.getInt("pad")), listOf(spm.bos, spm.eos, spm.pad))
        val cases = r.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val want = c.getJSONArray("ids").let { a -> (0 until a.length()).map(a::getInt) }
            assertEquals(c.getString("text"), want, spm.encode(c.getString("text")))
        }
    }

    // From reports written by other people (2026-10-10); they're no longer held out once rules were fixed on them.
    @Test fun rulesHandleEndingsPronounsAndCompanions() {
        val a = Dictionary.extract("Help po, na trap ako sa collapsed building. Hindi ako makalabas")
        assertEquals(Hazard.COLLAPSE, a.hazard); assertEquals(true, a.trapped); assertEquals(1, a.people)
        assertEquals(true, Dictionary.trappedOf("Napapalibutan ako ng baha at nasa bubong ako ng bahay namin"))
        assertEquals(false, Dictionary.trappedOf("Nakalabas na kami, ligtas na")) // "nakalabas" (got out) is never "makalabas"
        assertEquals(true, Dictionary.trappedOf("hindi kami makalabas"))
        val b = Dictionary.extract("The room is filling with water. I'm trapped with my wife and two children.")
        assertEquals(4, b.people); assertTrue(Vuln.CHILD in b.vuln); assertTrue(Cond.WATER_RISING in b.cond)
        assertEquals(1, Dictionary.peopleOf("My location is near the church. I'm trapped and need immediate rescue."))
        assertEquals(null, Dictionary.extract("May bahay kami sa tabi ng simbahan").hazard) // "bahay" is not "baha"
    }

    // Second batch by other people (2026-10-10): text-speak, typos, rubble, and the CPR safety case.
    @Test fun rulesHandleTextSpeakTyposAndBreathing() {
        val a = Dictionary.extract("May kasama akong dalawang bata, pero hindi p kmi makalbas")
        assertEquals(3, a.people); assertEquals(true, a.trapped)
        assertEquals(true, Dictionary.trappedOf("May crack na ung pader at hindi namin mabuksan ung main door."))
        val b = Dictionary.extract("Nsa ilalim ako ng ruble pero nakakagalaw pa ung isang kamay ko")
        assertEquals(Hazard.COLLAPSE, b.hazard); assertEquals(true, b.trapped); assertTrue(Cond.PINNED in b.cond)
        val c = Dictionary.extract("Im having trouble breathing. theres dust everywhere after the earthquake")
        assertFalse(Cond.NOT_BREATHING in c.cond); assertTrue(Cond.INJURED in c.cond)
        assertEquals(null, Dictionary.floorOf("may bubog sa paa ko").takeIf { it == ROOF }) // "bubog" (glass) is not "bubong"
    }

    @Test fun trainedHeads() {
        // 2-d toy heads: hazard says FLOOD along x; BLEED fires along y.
        val h = Heads.parse("""{"softmax":{"hazard":{"labels":["NONE","FLOOD"],"W":[[0,0],[8,0]],"b":[0,-4],"min":0.9}},
            "sigmoid":{"HEAVY_BLEEDING":{"w":[0,10],"b":-5,"min":0.9}}}""")
        val x = floatArrayOf(1f, 0f); val y = floatArrayOf(0f, 1f); val none = floatArrayOf(0f, 0f)
        assertEquals("NONE", applyHeads(h, listOf(none)).picks["hazard"]) // unsure → the default label
        assertEquals("FLOOD", applyHeads(h, listOf(x)).picks["hazard"])
        // Whole report says nothing, one clause says bleeding: the highest probability over clauses counts.
        val both = applyHeads(h, listOf(none, y))
        assertTrue("HEAVY_BLEEDING" in both.labels)
        assertEquals("NONE", both.picks["hazard"])
        assertFalse("HEAVY_BLEEDING" in applyHeads(h, listOf(floatArrayOf(0f, 0.6f))).labels) // p 0.73 < 0.9
    }

    @Test fun chatRouting() {
        assertEquals(Route.NEXT, Dictionary.route("susunod", yesNoWaiting = false))
        assertEquals(Route.REPEAT, Dictionary.route("ano ulit", yesNoWaiting = false))
        assertEquals(Route.QUESTION, Dictionary.route("Pwede bang sindihan ang lighter para makakita?", yesNoWaiting = true))
        assertEquals(Route.QUESTION, Dictionary.route("should I run outside", yesNoWaiting = false))
        assertEquals(Route.ANSWER, Dictionary.route("parang oo, parang hindi", yesNoWaiting = true))
        assertEquals(Route.ANSWER, Dictionary.route("hindi", yesNoWaiting = true))
        // New facts always count, even while a yes/no question waits: never read as a plain "no".
        assertEquals(Route.REPORT, Dictionary.route("Hindi na humihinga si lola!", yesNoWaiting = true))
        assertEquals(Route.REPORT, Dictionary.route(demo, yesNoWaiting = false))
    }

    // The always-open mic drops what Whisper writes for noise, but never a real (even one-word) call for help.
    @Test fun micNoise() {
        listOf("Thank you.", " you", "[Music]", "(wind blowing)", "...", "Thanks for watching!", "Salamat po sa panonood.").forEach {
            assertTrue(it, Dictionary.noise(it))
        }
        listOf("Tulong!", "ulit", "next", "oo", "Thank you, the water is rising", demo).forEach { assertFalse(it, Dictionary.noise(it)) }
    }

    @Test fun faqValidator() {
        val ids = listOf("lighter", "shout")
        assertEquals("lighter", validateFaq("lighter", ids))
        assertEquals("shout", validateFaq(" \"Shout\".\nbecause…", ids))
        assertEquals(NO_FAQ, validateFaq("NONE", ids))
        assertNull(validateFaq("swim", ids)) // another protocol's id is invalid here
        assertNull(validateFaq("lighter or shout", ids))
    }

    @Test fun beaconLife() {
        val start = Sample(0, 4_000_000, 80)
        assertNull(hoursLeft(start, Sample(60_000, 3_990_000, 80))) // under 5 min
        assertNull(hoursLeft(start, Sample(3_600_000, 4_000_000, 80))) // no drop yet
        assertEquals(39.0, hoursLeft(start, Sample(3_600_000, 3_900_000, 79))!!, 0.01) // 100 mAh/h, 3.9 Ah left
        assertEquals(39.0, hoursLeft(Sample(0, 0, 80), Sample(3_600_000, 0, 78))!!, 0.01) // no counter: 2 %/h, 78 % left
    }
}
