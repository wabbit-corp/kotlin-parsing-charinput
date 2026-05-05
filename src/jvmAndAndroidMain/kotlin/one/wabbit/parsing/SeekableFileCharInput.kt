package one.wabbit.parsing

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.util.WeakHashMap

/**
 * [CharInput] backed by a seekable [FileChannel].
 *
 * The input records byte-position checkpoints while decoding so [reset] can reconstruct old marks
 * by seeking backward and decoding forward again. Capturing from a mark still requires the marked
 * text to remain in the current character buffer.
 *
 * @param Span span type produced by captures.
 * @param channel source file channel; closed by [close].
 * @param charset charset used to decode file bytes.
 * @param spanFactory factory used to build captured spans.
 * @param charCapacity maximum number of decoded characters retained in memory.
 * @param byteBufferSize byte-buffer size used while decoding.
 * @param checkpointChars maximum decoded-character distance between checkpoints.
 * @param checkpointBytes maximum byte distance between checkpoints.
 */
class SeekableFileCharInput<out Span>(
    private val channel: FileChannel,
    charset: Charset,
    private val spanFactory: SpanFactory<Span>,
    private val charCapacity: Int = 64 * 1024,
    private val byteBufferSize: Int = 64 * 1024,
    private val checkpointChars: Long = 1_000_000L,
    private val checkpointBytes: Long = 1_000_000L,
) : CharInput<Span>(), AutoCloseable {
    private val decoder: CharsetDecoder =
        charset
            .newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    private val byteBuf: ByteBuffer = ByteBuffer.allocate(byteBufferSize)
    private val buf: CharArray = CharArray(charCapacity)

    // absolute char window
    private var baseAbs: Long = 0L
    private var head: Int = 0
    private var tail: Int = 0
    private var tailLine: Long = 1L
    private var tailCol: Long = 1L

    // file position accounting for decoding
    private var fileStartPos: Long = channel.position()
    private var bytesReadTotal: Long = 0
    private var eofBytes: Boolean = false
    private var eofChars: Boolean = false

    override var line: Long = 1L
    override var column: Long = 1L
    override val index: Long
        get() = baseAbs + head

    override var current: Char = EOB

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

    // Weak mark registry to protect compaction
    private val activeMarks: WeakHashMap<Mark, Long> = WeakHashMap()

    private data class Checkpoint(
        val bytePos: Long,
        val charAbs: Long,
        val line: Long,
        val col: Long,
    )

    private val checkpoints = ArrayList<Checkpoint>()
    private var lastCheckpointCharAbs: Long = 0L
    private var lastCheckpointBytePos: Long = 0L

    private var lastCapAbs: Long = 0L
    private var lastCapLine: Long = 1L
    private var lastCapCol: Long = 1L

    init {
        byteBuf.limit(0) // empty buffer to begin with
        // first checkpoint at current file pos
        checkpoints.add(Checkpoint(fileStartPos, 0L, line, column))
        lastCheckpointCharAbs = 0L
        lastCheckpointBytePos = fileStartPos
        ensure(1)
        current = if (head < tail) buf[head] else EOB
    }

    override fun close() {
        channel.close()
    }

    override fun ensure(n: Int): Boolean {
        while ((tail - head) < n && !eofChars) {
            if (tail >= charCapacity) compact()

            // Refill if the decoder needs more bytes to complete a char
            if (!byteBuf.hasRemaining() && !eofBytes) {
                byteBuf.compact()
                val read = channel.read(byteBuf)
                if (read < 0) eofBytes = true else bytesReadTotal += read.toLong()
                byteBuf.flip()
            }

            val out = CharBuffer.wrap(buf, tail, charCapacity - tail)
            val outStart = out.position()
            val res = decoder.decode(byteBuf, out, eofBytes)
            if (res.isError) res.throwException()
            val produced = out.position() - outStart

            if (produced == 0 && res.isUnderflow && !eofBytes) {
                // Not enough bytes to produce a char; read more
                byteBuf.compact()
                val read = channel.read(byteBuf)
                if (read < 0) eofBytes = true else bytesReadTotal += read.toLong()
                byteBuf.flip()
                continue
            }

            if (produced > 0) {
                // see patch #3 below: update tail line/col before checkpointing
                updateTailLineCol(tail, tail + produced)
                tail += produced
                maybeCheckpoint()
            }

            if (eofBytes && !byteBuf.hasRemaining()) {
                val out2 = CharBuffer.wrap(buf, tail, charCapacity - tail)
                val start2 = out2.position()
                decoder.flush(out2)
                tail += (out2.position() - start2)
                eofChars = true
            }

            if (produced == 0 && eofBytes && !byteBuf.hasRemaining()) {
                eofChars = true
            }
        }
        current = if (head < tail) buf[head] else EOB
        return (tail - head) >= n || eofChars
    }

    private fun consumedBytePos(): Long {
        // Next byte to decode = fileStartPos + bytesReadTotal - byteBuf.remaining()
        return fileStartPos + bytesReadTotal - byteBuf.remaining()
    }

    private fun updateTailLineCol(from: Int, to: Int) {
        var i = from
        // NEW: if previous char was CR and the first new char is LF, skip the LF
        if (i < to && i > 0 && buf[i] == '\n' && buf[i - 1] == '\r') {
            // We already counted the newline on the CR in the previous chunk.
            i++
        }
        while (i < to) {
            val c = buf[i]
            val next = if (i + 1 < to) buf[i + 1] else EOB
            if (c == '\n' || (c == '\r' && next != '\n')) {
                tailLine += 1
                tailCol = 1
            } else {
                tailCol += 1
            }
            i++
        }
    }

    private fun maybeCheckpoint() {
        val charAbsNow = baseAbs + tail
        val bytePosNow = consumedBytePos()
        if (
            charAbsNow - lastCheckpointCharAbs >= checkpointChars ||
                bytePosNow - lastCheckpointBytePos >= checkpointBytes
        ) {
            checkpoints.add(Checkpoint(bytePosNow, charAbsNow, tailLine, tailCol))
            lastCheckpointCharAbs = charAbsNow
            lastCheckpointBytePos = bytePosNow
        }
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
        // Prefer multiplatform CharArray -> String
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
        if (rel in 0..tail) {
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
            return
        }
        // Outside current window: rebuild from nearest checkpoint ≤ mark.abs
        val idx =
            checkpoints
                .binarySearchBy(mark.abs) { it.charAbs }
                .let { if (it >= 0) it else (-it - 2).coerceAtLeast(0) }
        val cp = checkpoints[idx]
        // Reset decoder and byte buffer
        decoder.reset()
        byteBuf.clear()
        byteBuf.limit(0)
        channel.position(cp.bytePos)
        fileStartPos = cp.bytePos
        bytesReadTotal = 0
        eofBytes = false
        eofChars = false

        // Reset char window and cursor
        baseAbs = cp.charAbs
        head = 0
        tail = 0
        line = cp.line
        column = cp.col
        tailLine = cp.line
        tailCol = cp.col
        // Decode forward until we can position to mark.abs
        val toAdvance = (mark.abs - baseAbs).toInt()
        require(ensure(toAdvance + 1) && (tail - head) >= toAdvance) {
            "Cannot reconstruct to requested mark; file truncated or decoder underflow."
        }
        var remain = (mark.abs - baseAbs).toInt()
        while (remain > 0) {
            val c = buf[head++]
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
            remain--
        }
        current =
            if (head < tail) {
                buf[head]
            } else if (ensure(1) && head < tail) {
                buf[head]
            } else {
                EOB
            }

        lastCapAbs = mark.capAbs
        lastCapLine = mark.capLine
        lastCapCol = mark.capCol
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
        // If start outside window, we must reconstruct from disk: for simplicity, forbid capturing
        // expired mark
        require(startAbs >= baseAbs) {
            "Mark expired (outside buffer); for huge captures, use capture() streaming mode or increase capacity/checkpoints."
        }
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
        activeMarks.remove(mark)
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
}
