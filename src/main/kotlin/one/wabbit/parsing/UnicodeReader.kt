//package one.wabbit.parsing
//
//import kotlin.contracts.ExperimentalContracts
//import kotlin.contracts.InvocationKind
//import kotlin.contracts.contract
//
//sealed class UnicodeReader<out Span> {
//    abstract val index: Int
//    abstract val line: Int
//    abstract val column: Int
//    abstract val current: Char
//
//    fun pos(): Pos = Pos(line, column, index)
//
//    abstract fun advance(): Unit
//    fun advanceN(len: Int): Unit {
//        for (i in 0 until len) advance()
//    }
//
//    fun take(len: Int): String? {
//        val result = peekN(len)
//        if (result != null) advanceN(len)
//        return result
//    }
//
//    inline fun takeWhile(predicate: (Char) -> Boolean): Span {
//        val start = mark()
//        while (predicate(current)) advance()
//        return capture(start)
//    }
//
//    abstract fun peek(index: Int): Char
//    abstract fun peekN(index: Int, len: Int): String?
//    fun peekN(len: Int): String? = peekN(0, len)
//
//    interface Mark
//    abstract fun mark(): Mark
//    abstract fun reset(mark: Mark): Unit
//    abstract fun capture(mark: Mark): Span
//
//    @OptIn(ExperimentalContracts::class)
//    fun <R : Any> withMark(block: (Mark) -> R?): R? {
//        contract {
//            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
//        }
//
//        val mark = mark()
//        try {
//            val result = block(mark)
//            if (result != null) return result
//            reset(mark)
//            return null
//        } finally {
//            reset(mark)
//        }
//    }
//
//    abstract fun capture(): Span
//    fun captureCurrentChar(): Span {
//        val mark = mark()
//        advance()
//        return capture(mark)
//    }
//
//    class StringUnicodeReader<out Span>(val input: String, private val spanIn: SpanIn<Span>) : UnicodeReader<Span>() {
//        private val length = input.length
//        override var index = 0
//        override var line = 1
//        override var column = 1
//
//        override var current: Char =
//            if (input.isNotEmpty()) input[0]
//            else EOB
//
//        val currentCodePoint: Int
//            get() {
//                if (pos >= input.length) return 0
//                val high = input[pos]
//                if (!high.isHighSurrogate() || pos + 1 >= input.length) {
//                    return high.code
//                }
//                val low = input[pos + 1]
//                if (!low.isLowSurrogate()) {
//                    return high.code
//                }
//                return Char.toCodePoint(high, low)
//            }
//
//        override fun peek(index: Int): Char =
//            if (this.index + index < length) input[this.index + index]
//            else EOB
//
//        override fun peekN(index: Int, len: Int): String? {
//            val index = this.index + index
//            val end = index + len
//            if (end > length) return null
//            return input.substring(index, end)
//        }
//
//        private class Mark(
//            val markIndex: Int,
//            val markedLine: Int,
//            val markedColumn: Int
//        ) : CharInput.Mark {
//            override fun toString(): String {
//                return "Mark($markIndex, $markedLine, $markedColumn)"
//            }
//        }
//
//        override fun mark(): CharInput.Mark {
//            return Mark(index, line, column)
//        }
//
//        override fun reset(mark: CharInput.Mark) {
//            mark as Mark
//            index = mark.markIndex
//            line = mark.markedLine
//            column = mark.markedColumn
//            current = if (index < length) input[index] else EOB
//        }
//
//        override fun capture(mark: CharInput.Mark): Span {
//            mark as Mark
//            val raw = if (spanIn.hasRaw) input.substring(mark.markIndex, index) else null
//
//            val start = if (spanIn.hasPos) Pos(mark.markedLine, mark.markedColumn, mark.markIndex) else null
//            val end = if (spanIn.hasPos) Pos(line, column, index) else null
//            val result = spanIn.make(raw, start, end) // Span(raw, start, end)
//
//            //markIndex = index
//            //markedLine = line
//            //markedColumn = column
//
//            return result
//        }
//
//        private var markIndex = 0
//        private var markedLine: Int = 1
//        private var markedColumn: Int = 1
//
//        override fun advance() {
//            index += 1
//            if (current == '\n') {
//                line += 1
//                column = 1
//            } else {
//                column += 1
//            }
//            current = if (index < length) input[index] else EOB
//        }
//
//        override fun capture(): Span {
//            val raw = if (spanIn.hasRaw) input.substring(markIndex, index) else null
//
//            val start = if (spanIn.hasPos) Pos(markedLine, markedColumn, markIndex) else null
//            val end = if (spanIn.hasPos) Pos(line, column, index) else null
//            val result = spanIn.make(raw, start, end) // Span(raw, start, end)
//
//            markIndex = index
//            markedLine = line
//            markedColumn = column
//
//            return result
//        }
//
//        override fun toString(): String {
//            // Extract +- 5 characters around the current position.
//            // Up until a newline.
//            var start = maxOf(0, index - 1)
//            while (start > 0 && input[start] != '\n' && start >= index - 5) start -= 1
//
//            var end = minOf(length, index + 1)
//            while (end < length && input[end] != '\n' && end <= index + 5) end += 1
//
//            val before = input.substring(start, index)
//            val after = if (index < length - 1) input.substring(index + 1, end) else ""
//
//            return "$line:$column:{$before[$current]$after}"
//        }
//    }
//
//    companion object {
//        val EOB = Char.MAX_VALUE
//
//        fun withEmptySpans(input: String): UnicodeReader<EmptySpan> = StringUnicodeReader(input, EmptySpan.spanLike)
//        fun withPosOnlySpans(input: String): UnicodeReader<PosOnlySpan> = StringUnicodeReader(input, PosOnlySpan.spanLike)
//        fun withTextOnlySpans(input: String): UnicodeReader<TextOnlySpan> = StringUnicodeReader(input, TextOnlySpan.spanLike)
//        fun withTextAndPosSpans(input: String): UnicodeReader<TextAndPosSpan> = StringUnicodeReader(input, TextAndPosSpan.spanLike)
//    }
//}
