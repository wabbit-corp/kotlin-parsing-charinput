// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

/**
 * [CharInput] backed by an in-memory [String].
 *
 * This implementation supports arbitrary marks and resets because the complete input remains
 * available for the lifetime of the instance.
 *
 * @param Span span type produced by captures.
 * @property input source text scanned by this input.
 * @param spanFactory factory used to build captured spans.
 */
class InMemoryCharInput<out Span>(val input: String, private val spanFactory: SpanFactory<Span>) :
    CharInput<Span>() {
    private val length = input.length.toLong()
    override var index: Long = 0L
    override var line: Long = 1L
    override var column: Long = 1L

    override fun close() {
        // nothing to close
    }

    override var current: Char =
        if (input.isNotEmpty()) {
            input[0]
        } else {
            EOB
        }

    override fun ensure(n: Int): Boolean {
        if (n <= 0) return true
        return (index + n - 1) < length
    }

    override fun peek(index: Int): Char =
        if (this.index + index < length) {
            input[(this.index + index).toInt()]
        } else {
            EOB
        }

    override fun peekN(index: Int, len: Int): String? {
        val startIndex = this.index + index
        val endIndex = startIndex + len
        if (endIndex > length) return null
        return input.substring(startIndex.toInt(), endIndex.toInt())
    }

    private class Mark(val markIndex: Long, val markedLine: Long, val markedColumn: Long) :
        CharInput.Mark {
        override val pos: Pos
            get() = Pos(markedLine, markedColumn, markIndex)

        override fun toString(): String = "Mark($markIndex, $markedLine, $markedColumn)"
    }

    override fun mark(): CharInput.Mark = Mark(index, line, column)

    override fun reset(mark: CharInput.Mark) {
        mark as Mark
        index = mark.markIndex
        line = mark.markedLine
        column = mark.markedColumn
        current = if (index < length) input[index.toInt()] else EOB
    }

    override fun capture(mark: CharInput.Mark): Span {
        mark as Mark
        val raw =
            if (spanFactory.hasRawText) input.substring(mark.markIndex.toInt(), index.toInt())
            else null

        val range =
            if (spanFactory.hasAbsolutePositions) {
                PosRange(
                    Pos(mark.markedLine, mark.markedColumn, mark.markIndex),
                    Pos(line, column, index),
                )
            } else {
                null
            }

        val metrics =
            if (spanFactory.hasTextMetrics) {
                TextSpanMetrics.of(raw ?: input.substring(mark.markIndex.toInt(), index.toInt()))
            } else {
                null
            }

        val result = spanFactory.make(raw, range, metrics)

        return result
    }

    private var markIndex: Long = 0
    private var markedLine: Long = 1
    private var markedColumn: Long = 1

    override fun advance() {
        val c = current
        val next = if (index + 1 < length) input[(index + 1).toInt()] else EOB
        index += 1
        if (c == '\n' || (c == '\r' && next != '\n')) {
            line += 1
            column = 1
        } else {
            column += 1
        }
        current = if (index < length) input[index.toInt()] else EOB
    }

    override fun capture(): Span {
        val raw =
            if (spanFactory.hasRawText) input.substring(markIndex.toInt(), index.toInt()) else null

        val range =
            if (spanFactory.hasAbsolutePositions) {
                PosRange(Pos(markedLine, markedColumn, markIndex), Pos(line, column, index))
            } else {
                null
            }

        val metrics =
            if (spanFactory.hasTextMetrics) {
                TextSpanMetrics.of(raw ?: input.substring(markIndex.toInt(), index.toInt()))
            } else {
                null
            }

        val result = spanFactory.make(raw, range, metrics)

        markIndex = index
        markedLine = line
        markedColumn = column

        return result
    }

    /** Return a short diagnostic view around the current cursor. */
    override fun toString(): String {
        var start = (index - 1).coerceAtLeast(0)
        while (start > 0 && input[(start - 1).toInt()] != '\n' && start > index - 6) start--

        var end = (index + 1).coerceAtMost(length)
        while (end < length && input[end.toInt()] != '\n' && end < index + 6) end++

        val before = input.substring(start.toInt(), index.toInt())
        val after = if (index < length) input.substring((index + 1).toInt(), end.toInt()) else ""
        return "$line:$column:{$before[$current]$after}"
    }
}
