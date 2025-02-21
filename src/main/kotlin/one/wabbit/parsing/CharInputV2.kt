package one.wabbit.parsing

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@Serializable
data class SpanV2(val raw: String) {
    val lineBreaks: Int
    private val lastLineBreak: Int

    init {
        var lineBreaks = 0
        var lastLineBreak = -1
        val stringLength = raw.length
        for (i in 0 until stringLength) {
            if (raw[i] == '\n') {
                lineBreaks += 1
                lastLineBreak = i
            }
        }
        this.lineBreaks = lineBreaks
        this.lastLineBreak = lastLineBreak
    }
}



//interface ChunkSource<A> {
//    fun fetch(): Chunk<A>?
//}
//
//interface AsyncChunkSource<A> {
//    suspend fun fetch(): Chunk<A>?
//}

//@Serializable
//private class MarkState
//
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
//
//class CharChunkInput private constructor(
//    var current: Chars?,
//    val fetch: (suspend () -> Chars?)? = null
//) {
//    suspend fun advance() {
//        val fetch = this.fetch
//        if (fetch != null) {
//            while (true) {
//                val b = fetch()
//                if (b == null) {
//                    current = null
//                    return
//                } else if (b.length() != 0) {
//                    current = b
//                    return
//                }
//            }
//        } else {
//            current = null
//        }
//    }
//}
//
//class CharInputV2 private constructor(private val input: CharChunkInput) {
//    private var bufferIndex: Int = 0
//    private var activeMarks: MutableList<MarkState> = mutableListOf()
//
//    // These indices are automatically updated by the advance() method.
//    var index  = 0L
//    var line   = 1L
//    var column = 1L
//
//    var current: Char = run {
//        val chars = input.current
//        if (chars == null) EOB
//        else {
//            check(chars.length() != 0)
//            chars[bufferIndex]
//        }
//    }
//
//    suspend fun advance() {
//        var chars = input.current
//
//        if (chars == null) {
//            current = EOB
//            return
//        }
//
//        index += 1
//        if (current == '\n') {
//            line += 1
//            column = 1
//        } else {
//            column += 1
//        }
//
//        bufferIndex += 1
//        if (bufferIndex >= chars.length()) {
//            input.advance()
//            chars = input.current
//            bufferIndex = 0
//        }
//
//        if (chars == null) current = EOB
//        else {
//            current = chars[bufferIndex]
//        }
//    }
//
//    inner class Mark(val index: Int)
//
//    suspend fun withMark(block: suspend (Mark) -> Unit): Span {
//        try {
//            val mark = mark()
//            activeMarks.add(mark)
//            block(mark)
//        } finally {
//            activeMarks.remove(mark)
//        }
//    }
//
//    fun reset(mark: Mark): Unit
//    fun capture(mark: Mark): Span
//
//    fun capture(): Span
//    fun captureCurrentChar(): Span {
//        val mark = mark()
//        advance()
//        return capture(mark)
//    }
//
//    private class Mark(
//        val markIndex: Int,
//        val markedLine: Int,
//        val markedColumn: Int
//    ) : CharInput.Mark
//
//    override fun mark(): CharInput.Mark {
//        return Mark(index, line, column)
//    }
//
//    override fun reset(mark: CharInput.Mark) {
//        mark as Mark
//        index = mark.markIndex
//        line = mark.markedLine
//        column = mark.markedColumn
//        current = if (index < input.length) input[index] else EOB
//    }
//
//    override fun capture(mark: CharInput.Mark): Span {
//        mark as Mark
//        val raw = input.substring(mark.markIndex, index)
//
//        val start = Pos(mark.markedLine, mark.markedColumn, mark.markIndex)
//        val end = Pos(line, column, index)
//        val result = Span(raw, start, end)
//
//        return result
//    }
//
//    override fun toString(): String {
//        // Extract +- 5 characters around the current position.
//        // Up until a newline.
//        var start = maxOf(0, index - 1)
//        while (start > 0 && input[start] != '\n' && start >= index - 5) start -= 1
//
//        var end = minOf(input.length, index + 1)
//        while (end < input.length && input[end] != '\n' && end <= index + 5) end += 1
//
//        val before = input.substring(start, index)
//        val after = if (index < input.length - 1) input.substring(index + 1, end) else ""
//
//        return "$line:$column:{$before[$current]$after}"
//    }
//
//    companion object {
//        @JvmStatic
//        val EOB = Char.MAX_VALUE
//
//        fun of(input: String): CharInputV2 {
//            var state = 0
//            return CharInputV2 {
//                if (state == 0) {
//                    state += 1
//                    Chars.String(input)
//                } else {
//                    null
//                }
//            }
//        }
//
//        fun of(path: File, charset: Charset = StandardCharsets.UTF_8): CharInputV2 {
//            val chars = path.reader()
//            return CharInputV2 {
//                chars.read()
//            }
//        }
//    }
//}
