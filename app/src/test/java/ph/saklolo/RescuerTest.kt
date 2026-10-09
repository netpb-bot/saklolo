package ph.saklolo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescuerTest {
    private fun h(id: Int, tri: Tri, vuln: Set<Vuln> = emptySet(), age: Int = 0, at: Long = 600_000, code: Code = Code.COLLAPSE) =
        Heard(Record(id, code, tri, vuln = vuln, age = age), listOf(at to -70), at)

    @Test fun ranking() {
        val all = listOf(
            h(1, Tri.GREEN, age = 50),
            h(2, Tri.RED, age = 1),
            h(3, Tri.YELLOW, setOf(Vuln.CHILD)),
            h(4, Tri.RED, age = 9), // older RED first
            h(5, Tri.YELLOW, age = 30),
            h(6, Tri.RED, setOf(Vuln.ELDERLY)), // vulnerable beats older
            h(7, Tri.YELLOW, code = Code.SAFE),
            h(8, Tri.YELLOW, code = Code.ACK),
        )
        assertEquals(listOf(6, 4, 2, 3, 5, 1), rank(all).map { it.rec.id })
        assertEquals(1, safeCount(all))
    }

    @Test fun hotCold() {
        // median of the last 3 s: the -30 spike and the old -90s are ignored
        val s = listOf(0L to -90, 1_000L to -90, 8_000L to -80, 8_500L to -30, 9_000L to -80, 9_500L to -82)
        assertEquals(-80.0, smooth(s, 10_000)!!, 0.01)
        assertEquals(-90.0, smooth(s.take(2), 60_000)!!, 0.01) // nothing recent → last 5 heard
        assertNull(smooth(emptyList(), 0))
        assertEquals(listOf(0, 1, 2, 3), listOf(-85.0, -75.0, -65.0, -55.0).map(::band))
        assertEquals(1, band(-80.0))
        assertEquals(0.0, level(-120.0), 0.0)
        assertEquals(1.0, level(-20.0), 0.0)
    }

    @Test fun distance() {
        assertEquals(1.0, approxMeters(-65.0, ref1m = -65.0), 0.01)
        assertTrue(approxMeters(-80.0) > approxMeters(-70.0))
        assertEquals(listOf("< 2 m", "~5 m", "~10 m", "20 m+"), listOf(1.0, 5.0, 10.0, 30.0).map(::distanceBucket))
    }

    @Test fun liveCompass() {
        // dB above the moving median; strongest facing 90°
        val peak = (0 until 360 step 10).map { it.toFloat() to (if (it in 60..120) 6.0 else -3.0) }
        assertEquals(90f, bestHeading(peak)!!, 15f)
        assertEquals(90f, bestHeading(peak.filter { it.first < 180 })!!, 15f) // half the circle is enough
        assertNull(bestHeading((0 until 360 step 10).map { it.toFloat() to 0.0 })) // no contrast
        assertNull(bestHeading((0 until 120 step 10).map { it.toFloat() to if (it == 60) 9.0 else 0.0 })) // 4 sectors only
        assertEquals(345f, bestHeading((0 until 360 step 10).map { (it - 360f) to if (it >= 330) 8.0 else 0.0 })!!, 0.1f) // negative headings wrap
        assertEquals(20f, relativeDeg(10f, 350f), 0.01f) // right, across north
        assertEquals(-90f, relativeDeg(0f, 90f), 0.01f) // left
    }

    @Test fun autoRing() {
        val near = listOf(9_000L to -60, 9_500L to -61, 9_900L to -59)
        assertTrue(closeEnough(near, 10_000))
        assertFalse(closeEnough(near.take(2), 10_000)) // too few samples
        assertFalse(closeEnough(listOf(9_000L to -90, 9_500L to -40, 9_900L to -91), 10_000)) // one spike isn't close
        assertFalse(closeEnough(near, 20_000)) // stale
    }

    @Test fun gps() {
        // 0.001° north ≈ 111 m, bearing north; 0.001° east at the equator ≈ 111 m, bearing east
        assertEquals(111.2, distanceM(1_000_000, 12_000_000, 1_000_100, 12_000_000), 0.5)
        assertEquals(0.0, bearingDeg(1_000_000, 12_000_000, 1_000_100, 12_000_000), 0.5)
        assertEquals(90.0, bearingDeg(0, 0, 0, 100), 0.5)
        assertEquals("NE", compass8(40.0))
        assertEquals("N", compass8(350.0))
        assertEquals("240 m", distanceText(238.0))
        assertEquals("1.1 km", distanceText(1_100.0))
    }

    @Test fun briefingTemplate() {
        val r = Record(
            1, Code.COLLAPSE, Tri.RED, trapped = true, people = 4, vuln = setOf(Vuln.CHILD, Vuln.ELDERLY),
            cond = setOf(Cond.PINNED, Cond.HEAVY_BLEEDING), floor = 2, spot = Spot.BATHROOM, battery = 31,
        )
        assertEquals(
            "Red. Collapse. 4 people, trapped, including a child and an elderly person. 2nd floor, bathroom. " +
                "Heavy bleeding. Someone is pinned. No GPS. Battery 31 percent. Reported 14 minutes ago.",
            briefing(r, 14, fil = false),
        )
        assertEquals(
            "Yellow. Emergency. Number of people unknown. Battery 0 percent. Reported 0 minutes ago.",
            briefing(Record(2, Code.OTHER, lat = 0, lon = 0), 0, fil = false),
        )
    }
}
