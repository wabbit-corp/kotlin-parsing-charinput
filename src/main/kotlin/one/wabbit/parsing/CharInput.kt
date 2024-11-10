package one.wabbit.parsing

import kotlinx.serialization.Serializable
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Serializable data class Pos(val line: Int, val column: Int, val index: Int) {
    override fun toString(): String {
        return "$line:$column@$index"
    }

    companion object {
        val start = Pos(1, 1, 0)
    }
}

interface SpanIn<out Span> {
    val hasRaw: Boolean
    val hasPos: Boolean
    fun make(raw: String?, start: Pos?, end: Pos?): Span
}

interface SpanOut<in Span> {
    val hasRaw: Boolean
    val hasPos: Boolean
    fun raw(span: Span): String
    fun start(span: Span): Pos
    fun end(span: Span): Pos
}

interface SpanLike<Span> : SpanIn<Span>, SpanOut<Span> {
    fun combine(left: Span, right: Span): Span
}

@Serializable data object EmptySpan {
    val spanLike = object : SpanLike<EmptySpan> {
        override val hasRaw: Boolean = false
        override val hasPos: Boolean = false
        override fun make(raw: String?, start: Pos?, end: Pos?): EmptySpan = EmptySpan
        override fun raw(span: EmptySpan): String = error("No raw string for Span0")
        override fun start(span: EmptySpan): Pos = error("No start for Span0")
        override fun end(span: EmptySpan): Pos = error("No end for Span0")
        override fun combine(left: EmptySpan, right: EmptySpan): EmptySpan = EmptySpan
    }
}

@Serializable data class PosOnlySpan(val start: Pos, val end: Pos) {
    override fun toString(): String {
        return "PosOnlySpan($start~$end)"
    }

    companion object {
        val spanLike = object : SpanLike<PosOnlySpan> {
            override val hasRaw: Boolean = false
            override val hasPos: Boolean = true
            override fun make(raw: String?, start: Pos?, end: Pos?): PosOnlySpan = PosOnlySpan(start!!, end!!)
            override fun raw(span: PosOnlySpan): String = error("No raw string for Span1")
            override fun start(span: PosOnlySpan): Pos = span.start
            override fun end(span: PosOnlySpan): Pos = span.end
            override fun combine(left: PosOnlySpan, right: PosOnlySpan): PosOnlySpan = PosOnlySpan(left.start, right.end)
        }
    }
}

@Serializable data class TextOnlySpan(val raw: String) {
    companion object {
        val spanLike = object : SpanLike<TextOnlySpan> {
            override val hasRaw: Boolean = true
            override val hasPos: Boolean = false
            override fun make(raw: String?, start: Pos?, end: Pos?): TextOnlySpan = TextOnlySpan(raw!!)
            override fun raw(span: TextOnlySpan): String = span.raw
            override fun start(span: TextOnlySpan): Pos = error("No start for Span2")
            override fun end(span: TextOnlySpan): Pos = error("No end for Span2")
            override fun combine(left: TextOnlySpan, right: TextOnlySpan): TextOnlySpan = TextOnlySpan(left.raw + right.raw)
        }
    }
}

@Serializable data class TextAndPosSpan(val raw: String, val start: Pos, val end: Pos) {
    operator fun plus(other: TextAndPosSpan): TextAndPosSpan {
        check(end == other.start)
        return TextAndPosSpan(
            raw = raw + other.raw,
            start = start,
            end = other.end
        )
    }

    fun format(original: String): String {
        val lines = original.split('\n')

        // Format like this:
        // L42:  |  val startLinePrefix = startLine.substring(0, startColumn)
        //                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        // L43:  |  val startLineSuffix = startLine.substring(startColumn)
        //          ^^^^^^^^^^^^^^^^^^^
        // (assuming that the span starts on line 42 and ends on line 43 and
        //  the start column starts at startLine and ends at startLineSuffix)

        val maxLineDigits = end.line.toString().length

        val prefixLength = "L${end.line}:  |  ".length

        val builder = StringBuilder()

        for (i in start.line - 1 until end.line) {
            val line = lines[i]
            val lineNum = i + 1

            val lineNumStr = "%${maxLineDigits}d".format(lineNum)

            builder.append("L$lineNumStr:  |  $line\n")

            if (i == start.line - 1) {
                for (j in 0 until prefixLength + start.column - 1) {
                    builder.append(' ')
                }
                for (j in start.column - 1 until end.column) {
                    builder.append('^')
                }
                builder.append('\n')
            }
        }

        return builder.toString()
    }

    companion object {
        val spanLike = object : SpanLike<TextAndPosSpan> {
            override val hasRaw: Boolean = true
            override val hasPos: Boolean = true
            override fun make(raw: String?, start: Pos?, end: Pos?): TextAndPosSpan = TextAndPosSpan(raw!!, start!!, end!!)
            override fun raw(span: TextAndPosSpan): String = span.raw
            override fun start(span: TextAndPosSpan): Pos = span.start
            override fun end(span: TextAndPosSpan): Pos = span.end
            override fun combine(left: TextAndPosSpan, right: TextAndPosSpan): TextAndPosSpan = TextAndPosSpan(left.raw + right.raw, left.start, right.end)
        }
    }
}

sealed class CharInput<out Span> {
    abstract val index: Int
    abstract val line: Int
    abstract val column: Int
    abstract val current: Char

    fun pos(): Pos = Pos(line, column, index)

    abstract fun advance(): Unit
    fun advanceN(len: Int): Unit {
        for (i in 0 until len) advance()
    }

    fun take(len: Int): String? {
        val result = peekN(len)
        if (result != null) advanceN(len)
        return result
    }

    inline fun takeWhile(predicate: (Char) -> Boolean): Span {
        val start = mark()
        while (predicate(current)) advance()
        return capture(start)
    }

    abstract fun peek(index: Int): Char
    abstract fun peekN(index: Int, len: Int): String?
    fun peekN(len: Int): String? = peekN(0, len)

    interface Mark
    abstract fun mark(): Mark
    abstract fun reset(mark: Mark): Unit
    abstract fun capture(mark: Mark): Span
//    abstract fun capture1(mark: Mark): Span1
//    abstract fun capture0(mark: Mark): Span0

    @OptIn(ExperimentalContracts::class)
    fun <R : Any> withMark(block: (Mark) -> R?): R? {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        val mark = mark()
        try {
            val result = block(mark)
            if (result != null) return result
            reset(mark)
            return null
        } finally {
            reset(mark)
        }
    }

    abstract fun capture(): Span
    fun captureCurrentChar(): Span {
        val mark = mark()
        advance()
        return capture(mark)
    }

    class FromString<out Span>(val input: String, private val spanIn: SpanIn<Span>) : CharInput<Span>() {
        private val length = input.length
        override var index = 0
        override var line = 1
        override var column = 1

        override var current: Char =
            if (input.isNotEmpty()) input[0]
            else EOB

        override fun peek(index: Int): Char =
            if (this.index + index < length) input[this.index + index]
            else EOB

        override fun peekN(index: Int, len: Int): String? {
            val index = this.index + index
            val end = index + len
            if (end > length) return null
            return input.substring(index, end)
        }

        private class Mark(
            val markIndex: Int,
            val markedLine: Int,
            val markedColumn: Int
        ) : CharInput.Mark {
            override fun toString(): String {
                return "Mark($markIndex, $markedLine, $markedColumn)"
            }
        }

        override fun mark(): CharInput.Mark {
            return Mark(index, line, column)
        }

        override fun reset(mark: CharInput.Mark) {
            mark as Mark
            index = mark.markIndex
            line = mark.markedLine
            column = mark.markedColumn
            current = if (index < length) input[index] else EOB
        }

        override fun capture(mark: CharInput.Mark): Span {
            mark as Mark
            val raw = if (spanIn.hasRaw) input.substring(mark.markIndex, index) else null

            val start = if (spanIn.hasPos) Pos(mark.markedLine, mark.markedColumn, mark.markIndex) else null
            val end = if (spanIn.hasPos) Pos(line, column, index) else null
            val result = spanIn.make(raw, start, end) // Span(raw, start, end)

            //markIndex = index
            //markedLine = line
            //markedColumn = column

            return result
        }

        private var markIndex = 0
        private var markedLine: Int = 1
        private var markedColumn: Int = 1

        override fun advance() {
            index += 1
            if (current == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
            current = if (index < length) input[index] else EOB
        }

        override fun capture(): Span {
            val raw = if (spanIn.hasRaw) input.substring(markIndex, index) else null

            val start = if (spanIn.hasPos) Pos(markedLine, markedColumn, markIndex) else null
            val end = if (spanIn.hasPos) Pos(line, column, index) else null
            val result = spanIn.make(raw, start, end) // Span(raw, start, end)

            markIndex = index
            markedLine = line
            markedColumn = column

            return result
        }

        override fun toString(): String {
            // Extract +- 5 characters around the current position.
            // Up until a newline.
            var start = maxOf(0, index - 1)
            while (start > 0 && input[start] != '\n' && start >= index - 5) start -= 1

            var end = minOf(length, index + 1)
            while (end < length && input[end] != '\n' && end <= index + 5) end += 1

            val before = input.substring(start, index)
            val after = if (index < length - 1) input.substring(index + 1, end) else ""

            return "$line:$column:{$before[$current]$after}"
        }
    }

    companion object {
        val EOB = Char.MAX_VALUE

        fun withEmptySpans(input: String): CharInput<EmptySpan> = FromString(input, EmptySpan.spanLike)
        fun withPosOnlySpans(input: String): CharInput<PosOnlySpan> = FromString(input, PosOnlySpan.spanLike)
        fun withTextOnlySpans(input: String): CharInput<TextOnlySpan> = FromString(input, TextOnlySpan.spanLike)
        fun withTextAndPosSpans(input: String): CharInput<TextAndPosSpan> = FromString(input, TextAndPosSpan.spanLike)
    }
}
