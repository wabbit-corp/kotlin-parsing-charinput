package one.wabbit.parsing

import kotlinx.serialization.Serializable
import java.io.Reader
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Serializable data class Pos(val line: Long, val column: Long, val index: Long) {
    override fun toString(): String {
        return "$line:$column@$index"
    }

    companion object {
        val start = Pos(1, 1, 0)
    }
}

@Serializable data class PosRange(val start: Pos, val end: Pos) {
    override fun toString(): String {
        return "SpanPosition($start~$end)"
    }
}

interface SpanFactory<out Span> {
    val hasRawText: Boolean
    val hasAbsolutePositions: Boolean
    val hasTextMetrics: Boolean
    fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): Span
}

interface SpanAccess<in Span> {
    val hasRawText: Boolean
    val hasAbsolutePositions: Boolean
    val hasTextMetrics: Boolean
    fun raw(span: Span): String
    fun start(span: Span): Pos
    fun end(span: Span): Pos
}

interface SpanLike<Span> : SpanFactory<Span>, SpanAccess<Span> {
    fun combine(left: Span, right: Span): Span
}

@Serializable sealed interface AnySpan
@Serializable sealed interface TextSpan : AnySpan {
    val raw: String
}
@Serializable sealed interface PosSpan : AnySpan {
    val start: Pos
    val end: Pos
}

@Serializable data object EmptySpan : AnySpan {
    val spanLike = object : SpanLike<EmptySpan> {
        override val hasRawText: Boolean = false
        override val hasAbsolutePositions: Boolean = false
        override val hasTextMetrics: Boolean = false
        override fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): EmptySpan = EmptySpan
        override fun raw(span: EmptySpan): String = error("No raw string for EmptySpan")
        override fun start(span: EmptySpan): Pos = error("No start for EmptySpan")
        override fun end(span: EmptySpan): Pos = error("No end for EmptySpan")
        override fun combine(left: EmptySpan, right: EmptySpan): EmptySpan = EmptySpan
    }
}

@Serializable data class PosOnlySpan(override val start: Pos, override val end: Pos) : PosSpan {
    override fun toString(): String {
        return "PosOnlySpan($start~$end)"
    }

    companion object {
        val spanLike = object : SpanLike<PosOnlySpan> {
            override val hasRawText: Boolean = false
            override val hasAbsolutePositions: Boolean = true
            override val hasTextMetrics: Boolean = false
            override fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): PosOnlySpan =
                PosOnlySpan(range!!.start, range!!.end)
            override fun raw(span: PosOnlySpan): String = error("No raw string for Span1")
            override fun start(span: PosOnlySpan): Pos = span.start
            override fun end(span: PosOnlySpan): Pos = span.end
            override fun combine(left: PosOnlySpan, right: PosOnlySpan): PosOnlySpan {
                require(left.end == right.start) { "Non-contiguous spans: ${left.end} vs ${right.start}" }
                return PosOnlySpan(left.start, right.end)
            }
        }
    }
}

@Serializable data class TextOnlySpan(override val raw: String) : TextSpan {
    companion object {
        val spanLike = object : SpanLike<TextOnlySpan> {
            override val hasRawText: Boolean = true
            override val hasAbsolutePositions: Boolean = false
            override val hasTextMetrics: Boolean = false
            override fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): TextOnlySpan =
                TextOnlySpan(raw!!)
            override fun raw(span: TextOnlySpan): String = span.raw
            override fun start(span: TextOnlySpan): Pos = error("No start for Span2")
            override fun end(span: TextOnlySpan): Pos = error("No end for Span2")
            override fun combine(left: TextOnlySpan, right: TextOnlySpan): TextOnlySpan = TextOnlySpan(left.raw + right.raw)
        }
    }
}

@Serializable data class TextAndPosSpan(override val raw: String, override val start: Pos, override val end: Pos) : TextSpan, PosSpan {
    operator fun plus(other: TextAndPosSpan): TextAndPosSpan {
        check(end == other.start)
        return TextAndPosSpan(
            raw = raw + other.raw,
            start = start,
            end = other.end
        )
    }

    fun format(original: String): String {
        // These are all safe since `original` is a string, and strings have
        // Int-based indices.
        val startLine = start.line.toInt()
        val endLine = end.line.toInt()
        val startColumn = start.column.toInt()
        val endColumn = end.column.toInt()

        val lines = original.split(Regex("\r\n|\n|\r"))

        // Format like this:
        // L42:  |  val startLinePrefix = startLine.substring(0, startColumn)
        //                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        // L43:  |  val startLineSuffix = startLine.substring(startColumn)
        //          ^^^^^^^^^^^^^^^^^^^
        // (assuming that the span starts on line 42 and ends on line 43 and
        //  the start column starts at startLine and ends at startLineSuffix)


        val maxDigits = endLine.toString().length
        fun gutter(n: Int): String =
            "L" + n.toString().padStart(maxDigits, ' ') + ":  |  "

        val b = StringBuilder()
        for (ln in startLine..endLine) {
            val idx = ln - 1
            val text = if (idx in lines.indices) lines[idx] else ""
            val g = gutter(ln)
            b.append(g).append(text).append('\n')

            val underlineStart = when (ln) {
                startLine -> startColumn
                else      -> 1
            }
            val underlineEndExclusive = when (ln) {
                endLine -> endColumn
                else    -> text.length + 1 // columns are 1-based; +1 to underline to EOL
            }

            val s0 = (underlineStart - 1).coerceAtLeast(0)
            val e0 = underlineEndExclusive.coerceAtLeast(underlineStart)
            if (e0 > underlineStart) {
                repeat(g.length + s0) { b.append(' ') }
                repeat(e0 - underlineStart) { b.append('^') }
                b.append('\n')
            }
        }
        return b.toString()
    }

    companion object {
        val spanLike = object : SpanLike<TextAndPosSpan> {
            override val hasRawText: Boolean = true
            override val hasAbsolutePositions: Boolean = true
            override val hasTextMetrics: Boolean = false
            override fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): TextAndPosSpan =
                TextAndPosSpan(raw!!, range!!.start, range!!.end)
            override fun raw(span: TextAndPosSpan): String = span.raw
            override fun start(span: TextAndPosSpan): Pos = span.start
            override fun end(span: TextAndPosSpan): Pos = span.end
            override fun combine(left: TextAndPosSpan, right: TextAndPosSpan): TextAndPosSpan {
                require(left.end == right.start) { "Non-contiguous spans: ${left.end} vs ${right.start}" }
                return TextAndPosSpan(left.raw + right.raw, left.start, right.end)
            }
        }
    }
}

//sealed class Chars {
//    abstract operator fun get(index: Int): Char
//    abstract fun length(): Int
//
//    data class Array(val array: CharArray) : Chars() {
//        override operator fun get(index: Int): Char = array[index]
//        override fun length(): Int = array.size
//    }
//    data class String(val string: kotlin.String) : Chars() {
//        override operator fun get(index: Int): Char = string[index]
//        override fun length(): Int = string.length
//    }
//}

//interface ChunkSource<A> {
//    fun fetch(): Chunk<A>?
//}
//
//interface AsyncChunkSource<A> {
//    suspend fun fetch(): Chunk<A>?
//}

sealed class CharInput<out Span> {
    abstract val index: Long
    abstract val line: Long
    abstract val column: Long
    abstract val current: Char

    abstract fun advance(): Unit
    /** Try to guarantee at least n chars of lookahead; false means EOF or cannot fill */
    abstract fun ensure(n: Int): Boolean
    abstract fun peek(index: Int): Char
    abstract fun peekN(index: Int, len: Int): String?

    interface Mark
    abstract fun mark(): Mark
    abstract fun reset(mark: Mark): Unit
    abstract fun capture(mark: Mark): Span
    abstract fun capture(): Span

    abstract fun close()

    fun pos(): Pos = Pos(line, column, index)

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
        while (current != EOB && predicate(current)) advance()
        return capture(start)
    }

    inline fun takeStringWhile(predicate: (Char) -> Boolean): String {
        val sb = StringBuilder()
        while (current != EOB && predicate(current)) {
            sb.append(current)
            advance()
        }
        return sb.toString()
    }

    fun readUntilNewline(): String {
        val sb = StringBuilder()
        while (current != EOB && current != '\n' && current != '\r') {
            sb.append(current)
            advance()
        }
        return sb.toString()
    }

    fun skipNewline(): Boolean {
        var found = false
        if (current == '\r') {
            found = true
            advance()
        }
        if (current == '\n') {
            found = true
            advance()
        }
        return found
    }

    fun peekN(len: Int): String? = peekN(0, len)

    fun takeExact(lit: String, ignoreCase: Boolean = false): Boolean {
        if (lit.isEmpty()) return true
        if (current == EOB) return false

        if (ignoreCase) {
            val p = peekN(lit.length) ?: return false
            if (!p.equals(lit, ignoreCase = true)) return false
            advanceN(lit.length)
            return true
        } else {
            if (peekN(lit.length) != lit) return false
            advanceN(lit.length)
            return true
        }
    }

    @OptIn(ExperimentalContracts::class)
    fun <R : Any> withMark(block: (Mark) -> R?): R? {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

        val m = mark()
        var committed = false
        try {
            val r = block(m)
            if (r != null) {
                committed = true  // keep progress
                return r
            }
            return null
        } finally {
            if (!committed) reset(m)  // only rewind on failure
        }
    }

    fun captureCurrentChar(): Span {
        val mark = mark()
        advance()
        return capture(mark)
    }

    companion object {
        val EOB = Char.MAX_VALUE

        init {
            require(!EOB.isWhitespace())
            require(!EOB.isLetterOrDigit())
            require(!EOB.isISOControl())
        }

        fun withEmptySpans(input: String): CharInput<EmptySpan> = InMemoryCharInput(input, EmptySpan.spanLike)
        fun withPosOnlySpans(input: String): CharInput<PosOnlySpan> = InMemoryCharInput(input, PosOnlySpan.spanLike)
        fun withTextOnlySpans(input: String): CharInput<TextOnlySpan> = InMemoryCharInput(input, TextOnlySpan.spanLike)
        fun withTextAndPosSpans(input: String): CharInput<TextAndPosSpan> = InMemoryCharInput(input, TextAndPosSpan.spanLike)

        // New factories
        fun <S> ring(reader: Reader, spanFactory: SpanFactory<S>, capacity: Int = 64 * 1024): CharInput<S> =
            RingBufferCharInput(reader, spanFactory, capacity)

        fun <S> streaming(reader: Reader, spanFactory: SpanFactory<S>, capacity: Int = 64 * 1024): CharInput<S> =
            StreamingCharInput(reader, spanFactory, capacity)

        fun <S> seekable(
            channel: FileChannel,
            charset: Charset,
            spanFactory: SpanFactory<S>,
            charCapacity: Int = 64 * 1024,
            byteBufferSize: Int = 64 * 1024,
            checkpointChars: Long = 1_000_000L,
            checkpointBytes: Long = 1_000_000L
        ): CharInput<S> =
            SeekableFileCharInput(channel, charset, spanFactory, charCapacity, byteBufferSize, checkpointChars, checkpointBytes)
    }
}
