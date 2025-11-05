package one.wabbit.parsing

import kotlinx.serialization.Serializable

@Serializable
data class TextSpanMetrics(
    val length: Long,
    val newlineCount: Long,
    val firstLineLen: Long,
    val lastLineLen: Long,
    val endsWithNewline: Boolean,
    val startsWithLF: Boolean,   // s.isNotEmpty() && s[0] == '\n'
    val endsWithCR: Boolean      // s.isNotEmpty() && s.last() == '\r'
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
        val zero = TextSpanMetrics(
            0, 0, 0, 0,
            false, false, false)

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
                        newlineCount++; lastBreak = i + 1
                        if (newlineCount == 1) firstLineLen = i
                        i += 2; continue
                    } else {
                        newlineCount++; lastBreak = i
                        if (newlineCount == 1) firstLineLen = i
                        i += 1; continue
                    }
                }
                if (c == '\n') {
                    newlineCount++; lastBreak = i
                    if (newlineCount == 1) firstLineLen = i
                }
                i++
            }
            val endsWithNewline = length > 0 && (s[length - 1] == '\n' || s[length - 1] == '\r')
            val lastLineLen = if (lastBreak >= 0) length - lastBreak - 1 else length
            val startsLF = length > 0 && s[0] == '\n'
            val endsCR = length > 0 && s[length - 1] == '\r'
            return TextSpanMetrics(
                length.toLong(), newlineCount.toLong(),
                firstLineLen.toLong(), lastLineLen.toLong(),
                endsWithNewline, startsLF, endsCR)
        }

        fun of(buf: CharArray, start: Int, endExclusive: Int): TextSpanMetrics {
            var newlineCount = 0
            var firstLineLen = endExclusive - start
            var lastBreak = -1
            var i = start
            while (i < endExclusive) {
                val c = buf[i]
                if (c == '\r') {
                    if (i + 1 < endExclusive && buf[i + 1] == '\n') {
                        newlineCount++; lastBreak = i + 1
                        if (newlineCount == 1) firstLineLen = i - start
                        i += 2; continue
                    } else {
                        newlineCount++; lastBreak = i
                        if (newlineCount == 1) firstLineLen = i - start
                        i += 1; continue
                    }
                }
                if (c == '\n') {
                    newlineCount++; lastBreak = i
                    if (newlineCount == 1) firstLineLen = i - start
                }
                i++
            }
            val length = endExclusive - start
            val endsWithNewline = length > 0 && (buf[endExclusive - 1] == '\n' || buf[endExclusive - 1] == '\r')
            val lastLineLen = if (lastBreak >= start) endExclusive - lastBreak - 1 else length
            val startsLF = length > 0 && buf[start] == '\n'
            val endsCR = length > 0 && buf[endExclusive - 1] == '\r'
            return TextSpanMetrics(length.toLong(), newlineCount.toLong(),
                firstLineLen.toLong(), lastLineLen.toLong(),
                endsWithNewline, startsLF, endsCR)
        }
    }
    /** O(1) concatenation */
    operator fun plus(b: TextSpanMetrics): TextSpanMetrics {
        val a = this
        val length = a.length + b.length
        // Merge CR|LF at the boundary
        val boundaryMerge = if (a.endsWithCR && b.startsWithLF) 1 else 0
        val newlineCount = a.newlineCount + b.newlineCount - boundaryMerge
        val firstLineLen =
            if (a.newlineCount == 0L) a.firstLineLen + b.firstLineLen else a.firstLineLen
        val lastLineLen =
            if (b.newlineCount == 0L) a.lastLineLen + b.lastLineLen else b.lastLineLen
        val endsWithNewline = if (b.length == 0L) a.endsWithNewline else b.endsWithNewline
        val startsWithLF = if (a.length == 0L) b.startsWithLF else a.startsWithLF
        val endsWithCR = if (b.length == 0L) a.endsWithCR else b.endsWithCR
        return TextSpanMetrics(length, newlineCount, firstLineLen, lastLineLen, endsWithNewline, startsWithLF, endsWithCR)
    }
}
