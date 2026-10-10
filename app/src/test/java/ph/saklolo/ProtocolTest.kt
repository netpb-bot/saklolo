package ph.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProtocolTest {
    private val p = Protocols(File("src/main/assets/protocols.json").readText())

    private fun ids(hazard: Hazard?, vararg c: Cond) = p.plan(hazard, c.toSet()).map { it.first.id }

    @Test fun unsureAlwaysTakesSafer() {
        val questions = p.all.values.flatMap { it.nodes }.filter { it.isQuestion }
        assertEquals(4, questions.size)
        questions.forEach {
            assertEquals(it.id, it.safer, it.after(Ans.UNSURE))
            assertTrue(it.id, it.safer == it.yes || it.safer == it.no)
        }
        assertEquals("nb_push", p.all.getValue("NOT_BREATHING").node("nb_check").after(Ans.UNSURE))
    }

    @Test fun lifeThreatBeforeHazard() {
        assertEquals(listOf("BLEEDING", "COLLAPSE"), ids(Hazard.COLLAPSE, Cond.HEAVY_BLEEDING))
        assertEquals(listOf("NOT_BREATHING", "BLEEDING", "ROOF"), ids(Hazard.FLOOD, Cond.HEAVY_BLEEDING, Cond.NOT_BREATHING))
        assertEquals(listOf("COLLAPSE"), ids(Hazard.LANDSLIDE))
        assertEquals(emptyList<String>(), ids(null))
    }

    @Test fun cprStartsWithBreathsOnFlood() {
        assertEquals("nb_breaths", p.plan(Hazard.FLOOD, setOf(Cond.NOT_BREATHING)).first().second)
        assertEquals("nb_push", p.plan(Hazard.COLLAPSE, setOf(Cond.NOT_BREATHING)).first().second)
        assertEquals("nb_push", p.plan(null, setOf(Cond.NOT_BREATHING)).first().second)
    }

    @Test fun stepsUseWhatIsKnown() {
        val flood = p.all.getValue("ROOF")
        val roof = Facts(Hazard.FLOOD, floor = ROOF)
        val rising = setOf(Cond.WATER_RISING)
        // Never "go higher" to someone already on the roof.
        assertEquals("rf_roof", flood.resolve("rf_high", roof))
        assertEquals("rf_high", flood.resolve("rf_high", Facts(Hazard.FLOOD, floor = 2)))
        // Already said the water is rising: don't ask again.
        assertEquals("rf_high", flood.resolve("rf_rising", Facts(Hazard.FLOOD, cond = rising)))
        assertEquals("rf_roof", flood.resolve("rf_rising", roof.copy(cond = rising)))
        assertEquals("rf_rising", flood.resolve("rf_rising", roof))
        assertEquals("rf_swim", flood.resolve("rf_swim", roof))
    }

    @Test fun faqsAreApprovedAndMatchedSafely() {
        val faqs = p.all.values.flatMap { it.faqs }
        assertTrue(faqs.size >= 16)
        assertTrue(p.all.getValue("COLLAPSE").faqs.size + p.all.getValue("BLEEDING").faqs.size >= 8)
        assertEquals(faqs.size, faqs.map { it.id }.toSet().size)
        faqs.forEach { assertTrue(it.id, it.examples.isNotEmpty() && it.answer.en.isNotBlank() && it.answer.fil.isNotBlank()) }
        // The keyword baseline may miss, but never gives a wrong approved answer on the myth eval set.
        val set = org.json.JSONArray(File("src/main/assets/eval/myths.json").readText())
        (0 until set.length()).map { set.getJSONObject(it) }.forEach { c ->
            val got = Dictionary.matchFaq(c.getString("q"), p.all.getValue(c.getString("protocol")).faqs)
            assertTrue("${c.getString("q")} -> $got", got == c.getString("expect") || got == NO_FAQ)
        }
    }

    @Test fun contentIsComplete() {
        assertEquals(setOf("NOT_BREATHING", "BLEEDING", "COLLAPSE", "ROOF"), p.all.keys)
        assertEquals(setOf("what", "hurt", "count", "exit", "place"), p.followups.keys)
        (p.followups.values + p.followupsByHazard.values).forEach { assertTrue("$it", it.en.isNotBlank() && it.fil.isNotBlank()) }
        assertTrue(p.followupsByHazard.keys.all { (id, _) -> id in p.followups })
        // Each hazard gets its own wording; no override falls back to the default.
        assertEquals("Can you get to higher, dry ground?", p.followup("exit", Hazard.FLOOD).en)
        assertEquals("Can you get out?", p.followup("exit", Hazard.COLLAPSE).en)
        assertEquals("What happened?", p.followup("what", null).en)
        // A YES to "Is the water still rising?" reaches the rescuers, not just the next step.
        assertEquals(Cond.WATER_RISING, p.all.getValue("ROOF").node("rf_rising").fact)
        p.all.values.forEach { proto ->
            val known = proto.nodes.map { it.id }.toSet()
            (listOf(proto.title) + proto.nodes.flatMap { listOfNotNull(it.text, it.detail) }).forEach {
                assertTrue("${proto.id}: $it", it.en.isNotBlank() && it.fil.isNotBlank())
            }
            proto.nodes.forEach { n ->
                listOfNotNull(n.next, n.yes, n.no, n.safer, n.roof).forEach { assertTrue("${n.id} -> $it", it in known) }
            }
        }
    }
}
