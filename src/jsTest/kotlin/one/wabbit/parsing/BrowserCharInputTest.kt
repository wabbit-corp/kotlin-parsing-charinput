// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.parsing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BrowserCharInputTest {
    @Test fun stringsKeepMarksAndRawSpans() {
        val input = CharInput.withTextOnlySpans("first\nsecond")
        val start = input.mark()
        assertEquals("first", input.take(5))
        assertEquals("first", input.capture(start).raw)
        input.reset(start)
        assertEquals('f', input.current)
        input.close()
    }

    @Test fun jvmReadersAreExplicitlyUnsupported() {
        assertFailsWith<UnsupportedOperationException> { CharInput.ring(Any(), EmptySpan.spanLike) }
        assertFailsWith<UnsupportedOperationException> { CharInput.streaming(Any(), EmptySpan.spanLike) }
        assertFailsWith<UnsupportedOperationException> { CharInput.seekable(Any(), Any(), EmptySpan.spanLike) }
    }
}
