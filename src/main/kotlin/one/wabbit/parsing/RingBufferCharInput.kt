package one.wabbit.parsing

import java.io.Reader
import java.util.WeakHashMap

/** Bounded-backtracking ring-buffer over a Reader. */
class RingBufferCharInput<out Span>(
    private val reader: Reader,
    private val spanFactory: SpanFactory<Span>,
    private val capacity: Int = 64 * 1024,
) : CharInput<Span>(), AutoCloseable {
    private val buf = CharArray(capacity)
    private var baseAbs: Long = 0L // absolute offset of buf[0]
    private var head: Int = 0 // read cursor
    private var tail: Int = 0 // write cursor (exclusive)
    private var eofSeen = false

    override var line: Long = 1L
    override var column: Long = 1L
    override val index: Long
        get() = baseAbs + head

    override var current: Char = EOB

    // Weakly track active marks so compaction doesn't drop their region
    private val activeMarks: WeakHashMap<Mark, Long> = WeakHashMap()

    private data class Mark(
        val abs: Long,
        val line: Long,
        val col: Long,
        val capAbs: Long,
        val capLine: Long,
        val capCol: Long,
    ) : CharInput.Mark {
        override val pos: Pos
            get() = Pos(line, col, abs)
    }

    private var lastCapAbs: Long = 0L
    private var lastCapLine: Long = 1L
    private var lastCapCol: Long = 1L

    init {
        ensure(1)
        current = if (head < tail) buf[head] else EOB
    }

    override fun close() {
        reader.close()
    }

    override fun ensure(n: Int): Boolean {
        while ((tail - head) < n && !eofSeen) {
            if (tail >= capacity) compact()
            val space = capacity - tail
            if (space == 0) break
            val read = reader.read(buf, tail, space)
            if (read < 0) {
                eofSeen = true
                break
            }
            if (read == 0) break
            tail += read
        }
        current = if (head < tail) buf[head] else EOB
        return (tail - head) >= n || eofSeen
    }

    private fun earliestProtectedAbs(): Long {
        var min = lastCapAbs
        val it = activeMarks.values.iterator()
        while (it.hasNext()) {
            val v = it.next()
            if (v < min) min = v
        }
        return min
    }

    private fun compact() {
        val minProtected = earliestProtectedAbs()
        val drop = (minProtected - baseAbs).toInt().coerceAtLeast(0)
        if (drop <= 0) return
        System.arraycopy(buf, drop, buf, 0, tail - drop)
        head -= drop
        tail -= drop
        baseAbs += drop.toLong()
    }

    override fun advance() {
        ensure(1)
        if (head >= tail) {
            current = EOB
            return
        }
        val c = buf[head++]
        // PEEK next char without consuming more:
        val next =
            if (head < tail) {
                buf[head]
            } else {
                ensure(1)
                if (head < tail) buf[head] else EOB
            }

        if (c == '\n' || (c == '\r' && next != '\n')) {
            line += 1
            column = 1
        } else {
            column += 1
        }
        current =
            if (head < tail) {
                buf[head]
            } else if (ensure(1) && head < tail) {
                buf[head]
            } else {
                EOB
            }
    }

    override fun peek(index: Int): Char {
        ensure(index + 1)
        val pos = head + index
        return if (pos < tail) buf[pos] else EOB
    }

    override fun peekN(index: Int, len: Int): String? {
        if (!ensure(index + len)) return null
        val start = head + index
        val end = start + len
        if (end > tail) return null
        return buf.concatToString(start, end)
    }

    override fun mark(): CharInput.Mark {
        val m = Mark(this.index, line, column, lastCapAbs, lastCapLine, lastCapCol)
        activeMarks[m] = m.abs
        return m
    }

    override fun reset(mark: CharInput.Mark) {
        mark as Mark
        val rel = (mark.abs - baseAbs).toInt()
        require(rel in 0..tail) { "Mark expired" }
        head = rel
        line = mark.line
        column = mark.col
        lastCapAbs = mark.capAbs
        lastCapLine = mark.capLine
        lastCapCol = mark.capCol
        current =
            if (head < tail) {
                buf[head]
            } else if (ensure(1) && head < tail) {
                buf[head]
            } else {
                EOB
            }
    }

    private fun buildString(startAbs: Long, endAbs: Long): String {
        val len = (endAbs - startAbs).toInt().coerceAtLeast(0)
        if (len == 0) return ""
        val sb = StringBuilder(len.coerceAtMost(1 shl 20))
        var pos = startAbs
        while (pos < endAbs) {
            val rel = (pos - baseAbs).toInt()
            val chunk = minOf(endAbs - pos, (tail - rel).toLong()).toInt()
            sb.append(buf, rel, chunk)
            pos += chunk
        }
        return sb.toString()
    }

    override fun capture(mark: CharInput.Mark): Span {
        mark as Mark
        val startAbs = mark.abs
        val endAbs = index
        require(startAbs >= baseAbs) { "Mark expired (outside buffer)" }
        val startRel = (startAbs - baseAbs).toInt()
        val endRel = (endAbs - baseAbs).toInt()
        val raw = if (spanFactory.hasRawText) buildString(startAbs, endAbs) else null
        val metrics =
            if (spanFactory.hasTextMetrics) {
                (raw?.let { TextSpanMetrics.of(it) } ?: TextSpanMetrics.of(buf, startRel, endRel))
            } else {
                null
            }
        val range =
            if (spanFactory.hasAbsolutePositions) {
                PosRange(Pos(mark.line, mark.col, startAbs), Pos(line, column, endAbs))
            } else {
                null
            }
        activeMarks.remove(mark) // mark no longer needed
        return spanFactory.make(raw, range, metrics)
    }

    override fun capture(): Span {
        val startAbs = lastCapAbs
        val endAbs = index
        val raw = if (spanFactory.hasRawText) buildString(startAbs, endAbs) else null
        val metrics =
            if (spanFactory.hasTextMetrics) TextSpanMetrics.of(raw ?: buildString(startAbs, endAbs))
            else null
        val range =
            if (spanFactory.hasAbsolutePositions) {
                PosRange(Pos(lastCapLine, lastCapCol, startAbs), Pos(line, column, endAbs))
            } else {
                null
            }
        val s = spanFactory.make(raw, range, metrics)
        lastCapAbs = endAbs
        lastCapLine = line
        lastCapCol = column
        return s
    }

    override fun toString(): String {
        val idx = head
        var s = (idx - 1).coerceAtLeast(0)
        while (s > 0 && buf[s - 1] != '\n' && s > idx - 6) s--
        var e = (idx + 1).coerceAtMost(tail)
        while (e < tail && buf[e] != '\n' && e < idx + 6) e++
        val before = String(buf, s, idx - s)
        val cur = if (idx < tail) buf[idx] else '∅'
        val after = if (idx < tail) String(buf, idx + 1, e - (idx + 1)) else ""
        return "$line:$column:{$before[$cur]$after}"
    }
}
