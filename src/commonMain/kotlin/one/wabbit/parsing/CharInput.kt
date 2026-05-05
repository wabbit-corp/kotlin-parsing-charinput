// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.serialization.Serializable

/**
 * Absolute source position in a character input.
 *
 * Lines and columns are 1-based. [index] is a zero-based character offset from the beginning of the
 * input.
 *
 * @property line 1-based line number.
 * @property column 1-based column number.
 * @property index zero-based absolute character offset.
 */
@Serializable
data class Pos(val line: Long, val column: Long, val index: Long) {
    /** Format this position as `line:column@index`. */
    override fun toString(): String = "$line:$column@$index"

    companion object {
        /** First position in a character input. */
        val start = Pos(1, 1, 0)
    }
}

/**
 * Half-open source range bounded by [start] and [end].
 *
 * [start] is the position of the first captured character. [end] is the position immediately after
 * the captured text.
 *
 * @property start inclusive start position.
 * @property end exclusive end position.
 */
@Serializable
data class PosRange(val start: Pos, val end: Pos) {
    /** Format this range for diagnostics. */
    override fun toString(): String = "SpanPosition($start~$end)"
}

/**
 * Builds span values for captured input.
 *
 * The boolean flags tell [CharInput] implementations which capture data must be computed before
 * calling [make].
 *
 * @param Span span type produced by this factory.
 */
interface SpanFactory<out Span> {
    /** Whether [make] needs raw captured text. */
    val hasRawText: Boolean

    /** Whether [make] needs absolute start/end positions. */
    val hasAbsolutePositions: Boolean

    /** Whether [make] needs [TextSpanMetrics]. */
    val hasTextMetrics: Boolean

    /**
     * Build a span from the requested capture data.
     *
     * Values whose corresponding `has*` flag is `false` are passed as `null`.
     *
     * @param raw raw captured text, when requested.
     * @param range absolute source range, when requested.
     * @param metrics text metrics, when requested.
     * @return constructed span value.
     */
    fun make(raw: String?, range: PosRange?, metrics: TextSpanMetrics?): Span
}

/**
 * Reads data back out of span values.
 *
 * Accessors may throw when the span type does not carry the requested data. Check the corresponding
 * `has*` flag before calling.
 *
 * @param Span span type consumed by this accessor.
 */
interface SpanAccess<in Span> {
    /** Whether [raw] is supported. */
    val hasRawText: Boolean

    /** Whether [start] and [end] are supported. */
    val hasAbsolutePositions: Boolean

    /** Whether text metrics are supported by this span family. */
    val hasTextMetrics: Boolean

    /** Return raw captured text from [span]. */
    fun raw(span: Span): String

    /** Return the start position from [span]. */
    fun start(span: Span): Pos

    /** Return the end position from [span]. */
    fun end(span: Span): Pos
}

/**
 * Full span adapter that can both create and inspect spans.
 *
 * @param Span span type handled by this adapter.
 */
interface SpanLike<Span> : SpanFactory<Span>, SpanAccess<Span> {
    /**
     * Combine adjacent [left] and [right] spans.
     *
     * Implementations generally require `left.end == right.start` when positions are available.
     */
    fun combine(left: Span, right: Span): Span
}

/** Marker interface for built-in serializable span shapes. */
@Serializable sealed interface AnySpan

/** Span shape that carries raw captured text. */
@Serializable
sealed interface TextSpan : AnySpan {
    /** Raw captured text. */
    val raw: String
}

/** Span shape that carries absolute source positions. */
@Serializable
sealed interface PosSpan : AnySpan {
    /** Inclusive start position. */
    val start: Pos

    /** Exclusive end position. */
    val end: Pos
}

/** Span value that carries no text or position data. */
@Serializable
data object EmptySpan : AnySpan {
    /** Span adapter for [EmptySpan]. */
    val spanLike =
        object : SpanLike<EmptySpan> {
            override val hasRawText: Boolean = false
            override val hasAbsolutePositions: Boolean = false
            override val hasTextMetrics: Boolean = false

            override fun make(
                raw: String?,
                range: PosRange?,
                metrics: TextSpanMetrics?,
            ): EmptySpan = EmptySpan

            override fun raw(span: EmptySpan): String = error("No raw string for EmptySpan")

            override fun start(span: EmptySpan): Pos = error("No start for EmptySpan")

            override fun end(span: EmptySpan): Pos = error("No end for EmptySpan")

            override fun combine(left: EmptySpan, right: EmptySpan): EmptySpan = EmptySpan
        }
}

/**
 * Span value that carries only source positions.
 *
 * @property start inclusive start position.
 * @property end exclusive end position.
 */
@Serializable
data class PosOnlySpan(override val start: Pos, override val end: Pos) : PosSpan {
    /** Format this span for diagnostics. */
    override fun toString(): String = "PosOnlySpan($start~$end)"

    companion object {
        /** Span adapter for [PosOnlySpan]. */
        val spanLike =
            object : SpanLike<PosOnlySpan> {
                override val hasRawText: Boolean = false
                override val hasAbsolutePositions: Boolean = true
                override val hasTextMetrics: Boolean = false

                override fun make(
                    raw: String?,
                    range: PosRange?,
                    metrics: TextSpanMetrics?,
                ): PosOnlySpan {
                    val nonNullRange = range!!
                    return PosOnlySpan(nonNullRange.start, nonNullRange.end)
                }

                override fun raw(span: PosOnlySpan): String = error("No raw string for Span1")

                override fun start(span: PosOnlySpan): Pos = span.start

                override fun end(span: PosOnlySpan): Pos = span.end

                override fun combine(left: PosOnlySpan, right: PosOnlySpan): PosOnlySpan {
                    require(left.end == right.start) {
                        "Non-contiguous spans: ${left.end} vs ${right.start}"
                    }
                    return PosOnlySpan(left.start, right.end)
                }
            }
    }
}

/**
 * Span value that carries only raw captured text.
 *
 * @property raw captured text.
 */
@Serializable
data class TextOnlySpan(override val raw: String) : TextSpan {
    companion object {
        /** Span adapter for [TextOnlySpan]. */
        val spanLike =
            object : SpanLike<TextOnlySpan> {
                override val hasRawText: Boolean = true
                override val hasAbsolutePositions: Boolean = false
                override val hasTextMetrics: Boolean = false

                override fun make(
                    raw: String?,
                    range: PosRange?,
                    metrics: TextSpanMetrics?,
                ): TextOnlySpan = TextOnlySpan(raw!!)

                override fun raw(span: TextOnlySpan): String = span.raw

                override fun start(span: TextOnlySpan): Pos = error("No start for Span2")

                override fun end(span: TextOnlySpan): Pos = error("No end for Span2")

                override fun combine(left: TextOnlySpan, right: TextOnlySpan): TextOnlySpan =
                    TextOnlySpan(left.raw + right.raw)
            }
    }
}

/**
 * Span value that carries both raw captured text and absolute source positions.
 *
 * @property raw captured text.
 * @property start inclusive start position.
 * @property end exclusive end position.
 */
@Serializable
data class TextAndPosSpan(
    override val raw: String,
    override val start: Pos,
    override val end: Pos,
) : TextSpan, PosSpan {
    /**
     * Combine this span with an adjacent [other] span.
     *
     * @throws IllegalStateException when the spans are not contiguous.
     */
    operator fun plus(other: TextAndPosSpan): TextAndPosSpan {
        check(end == other.start)
        return TextAndPosSpan(raw = raw + other.raw, start = start, end = other.end)
    }

    /**
     * Format a source excerpt underlining this span within [original].
     *
     * @param original complete original source text.
     * @return multi-line diagnostic excerpt.
     */
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

        fun gutter(n: Int): String = "L" + n.toString().padStart(maxDigits, ' ') + ":  |  "

        val b = StringBuilder()
        for (ln in startLine..endLine) {
            val idx = ln - 1
            val text = if (idx in lines.indices) lines[idx] else ""
            val g = gutter(ln)
            b.append(g).append(text).append('\n')

            val underlineStart =
                when (ln) {
                    startLine -> startColumn
                    else -> 1
                }
            val underlineEndExclusive =
                when (ln) {
                    endLine -> endColumn
                    else -> text.length + 1 // columns are 1-based; +1 to underline to EOL
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
        /** Span adapter for [TextAndPosSpan]. */
        val spanLike =
            object : SpanLike<TextAndPosSpan> {
                override val hasRawText: Boolean = true
                override val hasAbsolutePositions: Boolean = true
                override val hasTextMetrics: Boolean = false

                override fun make(
                    raw: String?,
                    range: PosRange?,
                    metrics: TextSpanMetrics?,
                ): TextAndPosSpan {
                    val nonNullRaw = raw!!
                    val nonNullRange = range!!
                    return TextAndPosSpan(nonNullRaw, nonNullRange.start, nonNullRange.end)
                }

                override fun raw(span: TextAndPosSpan): String = span.raw

                override fun start(span: TextAndPosSpan): Pos = span.start

                override fun end(span: TextAndPosSpan): Pos = span.end

                override fun combine(left: TextAndPosSpan, right: TextAndPosSpan): TextAndPosSpan {
                    require(left.end == right.start) {
                        "Non-contiguous spans: ${left.end} vs ${right.start}"
                    }
                    return TextAndPosSpan(left.raw + right.raw, left.start, right.end)
                }
            }
    }
}

// sealed class Chars {
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
// }

// interface ChunkSource<A> {
//    fun fetch(): Chunk<A>?
// }
//
// interface AsyncChunkSource<A> {
//    suspend fun fetch(): Chunk<A>?
// }

/**
 * Mutable character input cursor with lookahead, marks, resets, and span capture.
 *
 * Implementations expose [current] as the character under the cursor or [EOB] when no character
 * remains. Calling [advance] moves the cursor forward and updates [line], [column], and [index].
 *
 * @param Span span type produced by [capture].
 */
abstract class CharInput<out Span> {
    /** Zero-based absolute character offset. */
    abstract val index: Long

    /** Current 1-based line number. */
    abstract val line: Long

    /** Current 1-based column number. */
    abstract val column: Long

    /** Character under the cursor, or [EOB] at end of buffer/input. */
    abstract val current: Char

    /** Advance the cursor by one character when possible. */
    abstract fun advance(): Unit

    /**
     * Try to guarantee at least [n] characters of lookahead.
     *
     * @return `false` when EOF or the implementation cannot fill that far.
     */
    abstract fun ensure(n: Int): Boolean

    /**
     * Return the character [index] positions ahead of [current], or [EOB] beyond available input.
     */
    abstract fun peek(index: Int): Char

    /** Return [len] characters starting [index] positions ahead, or `null` if unavailable. */
    abstract fun peekN(index: Int, len: Int): String?

    /** Resettable cursor mark. */
    interface Mark {
        /** Position at which the mark was created. */
        val pos: Pos
    }

    /** Create a mark at the current cursor position. */
    abstract fun mark(): Mark

    /**
     * Reset this input to [mark].
     *
     * Implementations may reject expired marks when the backing buffer no longer protects the
     * marked region.
     */
    abstract fun reset(mark: Mark): Unit

    /** Capture a span from [mark] to the current cursor. */
    abstract fun capture(mark: Mark): Span

    /**
     * Capture a span from the previous unmarked capture boundary to the current cursor, then move
     * that boundary to the current cursor.
     */
    abstract fun capture(): Span

    /** Close the underlying input resource. */
    abstract fun close()

    /** Return the current [Pos]. */
    fun pos(): Pos = Pos(line, column, index)

    /** Advance exactly [len] times. */
    fun advanceN(len: Int) {
        for (i in 0 until len) advance()
    }

    /**
     * Take [len] characters if available.
     *
     * @return consumed text, or `null` when [len] characters are not available.
     */
    fun take(len: Int): String? {
        val result = peekN(len)
        if (result != null) advanceN(len)
        return result
    }

    /** Consume characters while [predicate] returns `true` and return the captured span. */
    inline fun takeWhile(predicate: (Char) -> Boolean): Span {
        val start = mark()
        while (current != EOB && predicate(current)) advance()
        return capture(start)
    }

    /** Consume characters while [predicate] returns `true` and return the consumed text. */
    inline fun takeStringWhile(predicate: (Char) -> Boolean): String {
        val sb = StringBuilder()
        while (current != EOB && predicate(current)) {
            sb.append(current)
            advance()
        }
        return sb.toString()
    }

    /** Read characters until CR, LF, or [EOB], without consuming the newline. */
    fun readUntilNewline(): String {
        val sb = StringBuilder()
        while (current != EOB && current != '\n' && current != '\r') {
            sb.append(current)
            advance()
        }
        return sb.toString()
    }

    /**
     * Consume one newline sequence.
     *
     * Handles `\r`, `\n`, and `\r\n`.
     *
     * @return `true` when a newline was consumed.
     */
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

    /** Return [len] characters from the current cursor, or `null` if unavailable. */
    fun peekN(len: Int): String? = peekN(0, len)

    /**
     * Consume [lit] exactly when it appears at the current cursor.
     *
     * @param lit literal to match.
     * @param ignoreCase whether to compare case-insensitively.
     * @return `true` when [lit] was consumed.
     */
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

    /**
     * Run [block] with a mark and reset on `null`.
     *
     * A non-null return value commits input progress. A `null` return rewinds to the mark.
     * Exceptions thrown by [block] are not caught; the input is reset before the exception
     * propagates.
     */
    @OptIn(ExperimentalContracts::class)
    fun <R : Any> withMark(block: (Mark) -> R?): R? {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

        val m = mark()
        var committed = false
        try {
            val r = block(m)
            if (r != null) {
                committed = true // keep progress
                return r
            }
            return null
        } finally {
            if (!committed) reset(m) // only rewind on failure
        }
    }

    /** Capture the current character as a span and advance past it. */
    fun captureCurrentChar(): Span {
        val mark = mark()
        advance()
        return capture(mark)
    }

    companion object {
        /** End-of-buffer sentinel returned by [current] and [peek]. */
        val EOB = Char.MAX_VALUE

        init {
            require(!EOB.isWhitespace())
            require(!EOB.isLetterOrDigit())
            require(!EOB.isISOControl())
        }

        /** Create an in-memory input whose spans carry no data. */
        fun withEmptySpans(input: String): CharInput<EmptySpan> =
            InMemoryCharInput(input, EmptySpan.spanLike)

        /** Create an in-memory input whose spans carry positions only. */
        fun withPosOnlySpans(input: String): CharInput<PosOnlySpan> =
            InMemoryCharInput(input, PosOnlySpan.spanLike)

        /** Create an in-memory input whose spans carry raw text only. */
        fun withTextOnlySpans(input: String): CharInput<TextOnlySpan> =
            InMemoryCharInput(input, TextOnlySpan.spanLike)

        /** Create an in-memory input whose spans carry raw text and positions. */
        fun withTextAndPosSpans(input: String): CharInput<TextAndPosSpan> =
            InMemoryCharInput(input, TextAndPosSpan.spanLike)

        /**
         * Create a JVM/Android ring-buffer input from a `java.io.Reader`.
         *
         * Native targets throw [UnsupportedOperationException].
         */
        fun <S> ring(
            reader: Any,
            spanFactory: SpanFactory<S>,
            capacity: Int = 64 * 1024,
        ): CharInput<S> = ringCharInput(reader, spanFactory, capacity)

        /**
         * Create a JVM/Android streaming input from a `java.io.Reader`.
         *
         * Native targets throw [UnsupportedOperationException].
         */
        fun <S> streaming(
            reader: Any,
            spanFactory: SpanFactory<S>,
            capacity: Int = 64 * 1024,
        ): CharInput<S> = streamingCharInput(reader, spanFactory, capacity)

        /**
         * Create a JVM/Android seekable file input from a `FileChannel` and `Charset`.
         *
         * Native targets throw [UnsupportedOperationException].
         */
        fun <S> seekable(
            channel: Any,
            charset: Any,
            spanFactory: SpanFactory<S>,
            charCapacity: Int = 64 * 1024,
            byteBufferSize: Int = 64 * 1024,
            checkpointChars: Long = 1_000_000L,
            checkpointBytes: Long = 1_000_000L,
        ): CharInput<S> =
            seekableCharInput(
                channel,
                charset,
                spanFactory,
                charCapacity,
                byteBufferSize,
                checkpointChars,
                checkpointBytes,
            )
    }
}

internal expect fun <S> ringCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int = 64 * 1024,
): CharInput<S>

internal expect fun <S> streamingCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int = 64 * 1024,
): CharInput<S>

internal expect fun <S> seekableCharInput(
    channel: Any,
    charset: Any,
    spanFactory: SpanFactory<S>,
    charCapacity: Int = 64 * 1024,
    byteBufferSize: Int = 64 * 1024,
    checkpointChars: Long = 1_000_000L,
    checkpointBytes: Long = 1_000_000L,
): CharInput<S>
