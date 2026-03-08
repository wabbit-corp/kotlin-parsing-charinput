package one.wabbit.parsing

import kotlin.test.Test
import kotlin.test.assertEquals

class TextSpanMetricsTests {
    @Test
    fun testMonoidLaws() {
        val m = TextSpanMetrics
        val samples =
            listOf(
                "",
                "a",
                "abc",
                "\n",
                "\r",
                "\r\n",
                "line1\nline2",
                "line1\rline2",
                "line1\r\nline2",
            )

        // Monoid identity
        for (a in samples) {
            assertEquals(m.of(a), m.zero + m.of(a))
            assertEquals(m.of(a), m.of(a) + m.zero)
        }

        // Monoid associativity
        for (a in samples) {
            for (b in samples) {
                for (c in samples) {
                    assertEquals(m.of(a) + (m.of(b) + m.of(c)), (m.of(a) + m.of(b)) + m.of(c))
                }
            }
        }

        // Monoid homomorphism
        for (a in samples) {
            for (b in samples) {
                val ab = a + b
                assertEquals(m.of(ab), m.of(a) + m.of(b))
            }
        }

        // Corner cases:
        assertEquals(m.zero, m.zero + m.zero)
    }

    @Test
    fun testFlagsSemantics() {
        fun f(s: String) = TextSpanMetrics.of(s)
        assertEquals(true, f("\n").startsWithLF)
        assertEquals(false, f("\r").startsWithLF)
        assertEquals(true, f("\r").endsWithCR)
        assertEquals(false, f("\n").endsWithCR)
        assertEquals(true, f("\r").endsWithNewline)
        assertEquals(true, f("\n").endsWithNewline)
        assertEquals(false, f("").endsWithNewline)
    }

    @Test
    fun testCrLfMergesOnlyOncePerBoundary() {
        val m = TextSpanMetrics
        assertEquals(m.of("\r\n"), m.of("\r") + m.of("\n"))
        assertEquals(m.of("x\r\n"), m.of("x\r") + m.of("\n"))
        assertEquals(m.of("\r\nx"), m.of("\r") + m.of("\nx"))
        assertEquals(m.of("a\r\nb"), m.of("a\r") + m.of("\nb"))
        assertEquals(m.of("\r\n\r\n"), m.of("\r") + m.of("\n\r") + m.of("\n"))
    }
}
