package ph.saklolo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {
    private val full = Record(
        id = 0x1A2B3C4D, code = Code.COLLAPSE, tri = Tri.RED, trapped = true, worse = true,
        people = 4, vuln = setOf(Vuln.ELDERLY, Vuln.CHILD), cond = setOf(Cond.PINNED, Cond.HEAVY_BLEEDING),
        floor = 2, spot = Spot.BATHROOM, lat = 1_458_312, lon = 12_098_765, battery = 31, hops = 2, rev = 7, age = 300,
    )

    @Test fun roundTrip() {
        val b = full.encode()
        assertEquals(22, b.size)
        assertEquals(full, Record.decode(b))
        Code.entries.forEach { c -> assertEquals(c, Record.decode(full.copy(code = c).encode())!!.code) }
        assertEquals(65_535, Record.decode(full.copy(age = 70_000).encode())!!.age) // clamps, never wraps
    }

    @Test fun layout() {
        val b = full.encode()
        assertEquals(0x12, b[0].toInt() and 0xFF) // ver 1 | hops 2
        assertArrayEquals(byteArrayOf(0x1A, 0x2B, 0x3C, 0x4D), b.copyOfRange(1, 5))
        assertEquals(0b0000_10_1_1, b[13].toInt() and 0xFF) // COLLAPSE | RED | trapped | worse
        assertEquals(0x21, b[17].toInt()) // floor 2 | BATHROOM
        assertEquals(0xF7, Record(1, Code.FLOOD, floor = ROOF, spot = Spot.ATTIC).encode()[17].toInt() and 0xFF)
    }

    @Test fun noFixSentinel() {
        val r = Record.decode(Record(5, Code.OTHER).encode())!!
        assertEquals(NO_FIX, r.lat)
        assertEquals(NO_FIX, r.lon)
    }

    @Test fun rejectsJunk() {
        assertNull(Record.decode(ByteArray(21)))
        assertNull(Record.decode(ByteArray(22))) // ver 0
        assertNull(Record.decode(full.encode().also { it[13] = 0xF0.toByte() })) // code 15
    }

    @Test fun dedupe() {
        assertTrue(supersedes(full, null))
        assertTrue(supersedes(full.copy(rev = 8), full))
        assertTrue(supersedes(full.copy(battery = 20), full)) // same (id, rev) refreshes
        assertFalse(supersedes(full.copy(rev = 6), full)) // stale copy
        assertFalse(supersedes(full.copy(id = 9, rev = 8), full))
    }

    @Test fun bareSosRecord() {
        val r = Facts().toRecord(worse = false)
        assertEquals(Code.OTHER, r.code)
        assertEquals(Tri.YELLOW, r.tri)
    }
}
