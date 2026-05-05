// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

import java.io.Reader
import java.nio.channels.FileChannel
import java.nio.charset.Charset

internal actual fun <S> ringCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int,
): CharInput<S> {
    require(reader is Reader) { "reader must be java.io.Reader" }
    return RingBufferCharInput(reader, spanFactory, capacity)
}

internal actual fun <S> streamingCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int,
): CharInput<S> {
    require(reader is Reader) { "reader must be java.io.Reader" }
    return StreamingCharInput(reader, spanFactory, capacity)
}

internal actual fun <S> seekableCharInput(
    channel: Any,
    charset: Any,
    spanFactory: SpanFactory<S>,
    charCapacity: Int,
    byteBufferSize: Int,
    checkpointChars: Long,
    checkpointBytes: Long,
): CharInput<S> {
    require(channel is FileChannel) { "channel must be java.nio.channels.FileChannel" }
    require(charset is Charset) { "charset must be java.nio.charset.Charset" }
    return SeekableFileCharInput(
        channel = channel,
        charset = charset,
        spanFactory = spanFactory,
        charCapacity = charCapacity,
        byteBufferSize = byteBufferSize,
        checkpointChars = checkpointChars,
        checkpointBytes = checkpointBytes,
    )
}
