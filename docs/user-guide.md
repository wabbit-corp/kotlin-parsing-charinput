# User Guide

This guide shows the core `kotlin-parsing-charinput` workflow: create an input, consume characters,
capture spans, and use the default token parsers.

## Create An Input

Use an in-memory factory when your source text is already available as a `String`:

```kotlin
import one.wabbit.parsing.CharInput

val input = CharInput.withTextAndPosSpans("answer = 42\n")
```

The span factory determines what capture data is available. `withTextAndPosSpans` captures the raw
source text and the start/end positions of each token.

## Read Tokens

`DefaultParsers` contains small helpers for common token shapes:

```kotlin
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.DefaultParsers
import one.wabbit.parsing.NumberStyle

val input = CharInput.withTextAndPosSpans("answer = 42\n")

val name = DefaultParsers.readIdentifier(input)
DefaultParsers.skipHorizontalSpace(input)
check(input.takeExact("="))
DefaultParsers.skipHorizontalSpace(input)
val number = DefaultParsers.readNumber(input, NumberStyle.Json)

check(name.value == "answer")
check(number.value == "42")
check(name.span.raw == "answer")
```

Parser functions advance the cursor as they succeed. If you need to decide between branches, use a
mark and reset:

```kotlin
val result = input.withMark { mark ->
    if (!input.takeExact("prefix:")) return@withMark null
    if (!(input.current.isLetter() || input.current == '_')) return@withMark null
    DefaultParsers.readIdentifier(input)
}

if (result == null) {
    // input was reset to the mark created by withMark
}
```

`withMark` commits input progress only when the block returns a non-null result. If the block throws,
the input is reset before the exception propagates.

## Capture Spans Manually

Use `mark()` and `capture(mark)` when a token parser needs custom scanning logic:

```kotlin
val start = input.mark()
while (input.current != CharInput.EOB && input.current.isLetter()) {
    input.advance()
}
val span = input.capture(start)
```

For `TextAndPosSpan`, the returned span includes:

- `raw`: consumed source text
- `start`: inclusive start `Pos`
- `end`: exclusive end `Pos`

Positions use 1-based line and column values plus a zero-based absolute character index.

## Choose A Span Shape

Built-in span choices trade capture detail for allocation and bookkeeping cost:

- `EmptySpan`: no capture data
- `PosOnlySpan`: start and end positions
- `TextOnlySpan`: raw source text
- `TextAndPosSpan`: raw source text plus positions

If you need custom metadata, implement `SpanFactory` and request only the data your span needs via
`hasRawText`, `hasAbsolutePositions`, and `hasTextMetrics`.

## Strings

`readString` returns the unescaped string value and captures the full source literal as the span:

```kotlin
import one.wabbit.parsing.StringStyle

val input = CharInput.withTextAndPosSpans("\"hello\\nworld\"")
val parsed = DefaultParsers.readString(input, StringStyle.Json)

check(parsed.value == "hello\nworld")
```

Available presets:

- `StringStyle.Json`: strict JSON strings
- `StringStyle.Json5Like`: JSON-like strings with single quotes
- `StringStyle.JavaCStyle`: Java/C-style double-quoted strings
- `StringStyle.RustLike`: Rust-style braced Unicode escapes
- `StringStyle.PermissiveLegacy`: compatibility behavior used by the old overload

## Numbers

Use `NumberStyle.Json` for strict JSON number syntax:

```kotlin
val input = CharInput.withTextOnlySpans("-12.5e+3")
val number = DefaultParsers.readNumber(input, NumberStyle.Json)

check(number.value == "-12.5e+3")
```

Use `NumberStyle.Decimal` when you need to tune signs, leading dots, trailing dots, underscores,
leading-zero behavior, or exponent markers.

## Reader And File Inputs

JVM and Android provide reader/file-backed inputs:

```kotlin
import java.io.StringReader
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.TextOnlySpan

val input = CharInput.ring(StringReader("alpha beta"), TextOnlySpan.spanLike)
```

Use the variants according to the source:

- `CharInput.ring(reader, spanFactory)`: bounded reader input with protected mark regions.
- `CharInput.streaming(reader, spanFactory)`: forward streaming input for short-lived marks.
- `CharInput.seekable(channel, charset, spanFactory)`: file input that can seek back to checkpoints.

These factories are JVM/Android-only. Native targets expose the same factory names but throw
`UnsupportedOperationException`.

## End Of Input

Check `CharInput.EOB` before treating `current` as source text:

```kotlin
while (input.current != CharInput.EOB) {
    input.advance()
}
```

`EOB` is `Char.MAX_VALUE`; it is a sentinel value, not a character read from the input.
