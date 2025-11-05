package one.wabbit.parsing

import one.wabbit.random.gen.Gen
import kotlin.test.Test
import kotlin.test.assertEquals

class TextSpanMetricsTests {
    @Test fun testMonoidLaws() {
        val M = TextSpanMetrics

        // Monoid identity
        Gen.foreach(Gen.string) { a ->
            assertEquals(M.of(a), M.zero + M.of(a))
            assertEquals(M.of(a), M.of(a) + M.zero)
        }

        // Monoid associativity
        Gen.foreach(Gen.string, Gen.string, Gen.string) { a, b, c ->
            assertEquals(
                M.of(a) + (M.of(b) + M.of(c)),
                (M.of(a) + M.of(b)) + M.of(c))
        }

        // Monoid homomorphism
        Gen.foreach(Gen.string, Gen.string) { a, b ->
            val m = a + b
            assertEquals(M.of(m), M.of(a) + M.of(b))
        }

        // Corner cases:
        assertEquals(M.zero, M.zero + M.zero)
    }

    @Test fun testFlagsSemantics() {
        fun f(s: String) = TextSpanMetrics.of(s)
        assertEquals(true,  f("\n").startsWithLF)
        assertEquals(false, f("\r").startsWithLF)
        assertEquals(true,  f("\r").endsWithCR)
        assertEquals(false, f("\n").endsWithCR)
        assertEquals(true,  f("\r").endsWithNewline)
        assertEquals(true,  f("\n").endsWithNewline)
        assertEquals(false, f("").endsWithNewline)
    }

    @Test fun testCrLfMergesOnlyOncePerBoundary() {
        val M = TextSpanMetrics
        assertEquals(M.of("\r\n"), M.of("\r") + M.of("\n"))
        assertEquals(M.of("x\r\n"), M.of("x\r") + M.of("\n"))
        assertEquals(M.of("\r\nx"), M.of("\r") + M.of("\nx"))
        assertEquals(M.of("a\r\nb"), M.of("a\r") + M.of("\nb"))
        assertEquals(M.of("\r\n\r\n"), M.of("\r") + M.of("\n\r") + M.of("\n"))
    }
}
