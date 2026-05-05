// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

import kotlinx.serialization.Serializable

/**
 * Line-oriented metrics for a captured text span.
 *
 * The metrics support constant-time concatenation through [plus]. Newlines are counted as logical
 * line breaks, with CRLF treated as one newline when both characters are inside the measured text
 * or across a concatenation boundary.
 *
 * @property length number of characters in the measured text.
 * @property newlineCount number of logical line breaks.
 * @property firstLineLen number of characters before the first logical line break.
 * @property lastLineLen number of characters after the last logical line break, or zero when the
 *   text ends with a newline.
 * @property endsWithNewline whether the text ends with CR or LF.
 * @property startsWithLF whether the text starts with LF, used to merge a preceding CR boundary.
 * @property endsWithCR whether the text ends with CR, used to merge a following LF boundary.
 */
@Serializable
data class TextSpanMetrics(
    val length: Long,
    val newlineCount: Long,
    val firstLineLen: Long,
    val lastLineLen: Long,
    val endsWithNewline: Boolean,
    val startsWithLF: Boolean,
    val endsWithCR: Boolean,
) {
    init {
        require(length >= 0 && newlineCount >= 0 && firstLineLen >= 0 && lastLineLen >= 0)

        if (length == 0L) {
            require(newlineCount == 0L)
            require(firstLineLen == 0L && lastLineLen == 0L)
            require(!endsWithNewline)
            require(!startsWithLF)
            require(!endsWithCR)
        } else {
            if (newlineCount == 0L) {
                require(firstLineLen == length)
                require(lastLineLen == length)
                require(!endsWithNewline)
                require(!startsWithLF)
                require(!endsWithCR)
            } else {
                if (endsWithNewline) require(lastLineLen == 0L) else require(lastLineLen > 0)
                require(firstLineLen + lastLineLen + newlineCount <= length)
                if (startsWithLF) {
                    require(firstLineLen == 0L)
                }
                if (endsWithCR) {
                    require(endsWithNewline)
                }
            }
        }
    }

    companion object {
        /** Metrics for an empty span. */
        val zero = TextSpanMetrics(0, 0, 0, 0, false, false, false)

        /**
         * Compute metrics for [s].
         *
         * CRLF is counted as one logical newline. Lone CR and lone LF are also counted as one
         * logical newline.
         */
        fun of(s: CharSequence): TextSpanMetrics {
            val length = s.length
            var newlineCount = 0
            var firstLineLen = length
            var lastBreak = -1
            var i = 0
            while (i < length) {
                val c = s[i]
                if (c == '\r') {
                    if (i + 1 < length && s[i + 1] == '\n') {
                        newlineCount++
                        lastBreak = i + 1
                        if (newlineCount == 1) firstLineLen = i
                        i += 2
                        continue
                    } else {
                        newlineCount++
                        lastBreak = i
                        if (newlineCount == 1) firstLineLen = i
                        i += 1
                        continue
                    }
                }
                if (c == '\n') {
                    newlineCount++
                    lastBreak = i
                    if (newlineCount == 1) firstLineLen = i
                }
                i++
            }
            val endsWithNewline = length > 0 && (s[length - 1] == '\n' || s[length - 1] == '\r')
            val lastLineLen = if (lastBreak >= 0) length - lastBreak - 1 else length
            val startsLF = length > 0 && s[0] == '\n'
            val endsCR = length > 0 && s[length - 1] == '\r'
            return TextSpanMetrics(
                length.toLong(),
                newlineCount.toLong(),
                firstLineLen.toLong(),
                lastLineLen.toLong(),
                endsWithNewline,
                startsLF,
                endsCR,
            )
        }

        /**
         * Compute metrics for a slice of [buf].
         *
         * @param buf source character buffer.
         * @param start inclusive slice start.
         * @param endExclusive exclusive slice end.
         */
        fun of(buf: CharArray, start: Int, endExclusive: Int): TextSpanMetrics {
            var newlineCount = 0
            var firstLineLen = endExclusive - start
            var lastBreak = -1
            var i = start
            while (i < endExclusive) {
                val c = buf[i]
                if (c == '\r') {
                    if (i + 1 < endExclusive && buf[i + 1] == '\n') {
                        newlineCount++
                        lastBreak = i + 1
                        if (newlineCount == 1) firstLineLen = i - start
                        i += 2
                        continue
                    } else {
                        newlineCount++
                        lastBreak = i
                        if (newlineCount == 1) firstLineLen = i - start
                        i += 1
                        continue
                    }
                }
                if (c == '\n') {
                    newlineCount++
                    lastBreak = i
                    if (newlineCount == 1) firstLineLen = i - start
                }
                i++
            }
            val length = endExclusive - start
            val endsWithNewline =
                length > 0 && (buf[endExclusive - 1] == '\n' || buf[endExclusive - 1] == '\r')
            val lastLineLen = if (lastBreak >= start) endExclusive - lastBreak - 1 else length
            val startsLF = length > 0 && buf[start] == '\n'
            val endsCR = length > 0 && buf[endExclusive - 1] == '\r'
            return TextSpanMetrics(
                length.toLong(),
                newlineCount.toLong(),
                firstLineLen.toLong(),
                lastLineLen.toLong(),
                endsWithNewline,
                startsLF,
                endsCR,
            )
        }
    }

    /**
     * Concatenate metrics for adjacent text spans in constant time.
     *
     * If this span ends with CR and [b] starts with LF, the boundary is counted as one CRLF newline
     * rather than two line breaks.
     */
    operator fun plus(b: TextSpanMetrics): TextSpanMetrics {
        val a = this
        val length = a.length + b.length
        // Merge CR|LF at the boundary
        val boundaryMerge = if (a.endsWithCR && b.startsWithLF) 1 else 0
        val newlineCount = a.newlineCount + b.newlineCount - boundaryMerge
        val firstLineLen =
            if (a.newlineCount == 0L) a.firstLineLen + b.firstLineLen else a.firstLineLen
        val lastLineLen = if (b.newlineCount == 0L) a.lastLineLen + b.lastLineLen else b.lastLineLen
        val endsWithNewline = if (b.length == 0L) a.endsWithNewline else b.endsWithNewline
        val startsWithLF = if (a.length == 0L) b.startsWithLF else a.startsWithLF
        val endsWithCR = if (b.length == 0L) a.endsWithCR else b.endsWithCR
        return TextSpanMetrics(
            length,
            newlineCount,
            firstLineLen,
            lastLineLen,
            endsWithNewline,
            startsWithLF,
            endsWithCR,
        )
    }
}
