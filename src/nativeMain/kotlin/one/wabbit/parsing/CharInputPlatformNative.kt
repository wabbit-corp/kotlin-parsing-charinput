// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

private fun unsupported(name: String): Nothing {
    throw UnsupportedOperationException("$name is only supported on JVM/Android")
}

internal actual fun <S> ringCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int,
): CharInput<S> = unsupported("CharInput.ring")

internal actual fun <S> streamingCharInput(
    reader: Any,
    spanFactory: SpanFactory<S>,
    capacity: Int,
): CharInput<S> = unsupported("CharInput.streaming")

internal actual fun <S> seekableCharInput(
    channel: Any,
    charset: Any,
    spanFactory: SpanFactory<S>,
    charCapacity: Int,
    byteBufferSize: Int,
    checkpointChars: Long,
    checkpointBytes: Long,
): CharInput<S> = unsupported("CharInput.seekable")
