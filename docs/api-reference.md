# API Reference

Generate exact signatures locally with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

This page summarizes the public surface and behavioral contracts that are most important when using
the generated API docs.

## Core Cursor

- `CharInput<Span>`: mutable cursor with `current`, `advance`, `peek`, `peekN`, `mark`, `reset`,
  and `capture`.
- `CharInput.Mark`: reset point created by `mark()`.
- `CharInput.EOB`: end-of-buffer sentinel returned by `current` and `peek`.
- `CharInput.pos()`: current `Pos`.
- `CharInput.advanceN(len)`: advances a fixed number of characters.
- `CharInput.take(len)`: consumes a fixed number of characters when available.
- `CharInput.takeWhile(predicate)`: consumes matching characters and returns a captured span.
- `CharInput.takeStringWhile(predicate)`: consumes matching characters and returns text.
- `CharInput.readUntilNewline()`: consumes through the current line without consuming the newline.
- `CharInput.skipNewline()`: consumes CR, LF, or CRLF.
- `CharInput.takeExact(lit, ignoreCase)`: consumes a literal when it appears at the cursor.
- `CharInput.withMark(block)`: resets on a null result, commits on a non-null result, and resets
  before rethrowing exceptions from the block.
- `CharInput.capture()`: captures from the previous implicit capture boundary to the current cursor
  and advances that boundary to the current cursor.
- `CharInput.captureCurrentChar()`: captures and consumes the current character.

## Positions And Spans

- `Pos`: 1-based line and column plus zero-based absolute character index.
- `PosRange`: half-open range from inclusive start to exclusive end.
- `SpanFactory<Span>`: builds spans from requested raw text, positions, and text metrics.
- `SpanAccess<Span>`: reads data back from span values.
- `SpanLike<Span>`: combined factory/accessor that can also merge adjacent spans.
- `AnySpan`: marker interface for built-in serializable span shapes.
- `TextSpan`: span shape with raw captured text.
- `PosSpan`: span shape with start/end positions.
- `EmptySpan`: span with no data.
- `PosOnlySpan`: span with positions only.
- `TextOnlySpan`: span with raw text only.
- `TextAndPosSpan`: span with raw text and positions.

## Input Factories

- `CharInput.withEmptySpans(input)`: in-memory input with `EmptySpan`.
- `CharInput.withPosOnlySpans(input)`: in-memory input with `PosOnlySpan`.
- `CharInput.withTextOnlySpans(input)`: in-memory input with `TextOnlySpan`.
- `CharInput.withTextAndPosSpans(input)`: in-memory input with `TextAndPosSpan`.
- `CharInput.ring(reader, spanFactory, capacity)`: JVM/Android reader input with bounded
  backtracking.
- `CharInput.streaming(reader, spanFactory, capacity)`: JVM/Android forward streaming input.
- `CharInput.seekable(channel, charset, spanFactory, ...)`: JVM/Android seekable file input.

The reader and file factories accept `Any` in common code so the common API can exist on every
target. JVM/Android validate the runtime type. Native targets throw `UnsupportedOperationException`.

## Default Parsers

- `Spanned<Span, T>`: parsed value plus captured span.
- `DefaultParsers.skipSpaces(input, start)`: consumes all Kotlin whitespace.
- `DefaultParsers.skipHorizontalSpace(input, start)`: consumes spaces and tabs only.
- `DefaultParsers.isEol(c)`: returns whether a character is CR or LF.
- `DefaultParsers.skipToEol(input)`: advances to but does not consume CR or LF.
- `DefaultParsers.readIdentifier(input)`: reads `[letter|_][letter|digit|_]*`.
- `DefaultParsers.readString(input, style)`: reads a quoted string and returns the unescaped value.
- `DefaultParsers.readString(input)`: compatibility overload using `StringStyle.PermissiveLegacy`.
- `DefaultParsers.readNumber(input, style)`: reads a configurable decimal number.
- `DefaultParsers.readNumber(input, errors)`: compatibility overload using callback errors.

## String Configuration

- `StringStyle`: quoted string grammar.
- `StringStyle.Json`: strict JSON string grammar.
- `StringStyle.Json5Like`: JSON-like strings with single quotes.
- `StringStyle.JavaCStyle`: Java/C-style double-quoted strings.
- `StringStyle.RustLike`: Rust-style braced Unicode escapes.
- `StringStyle.PermissiveLegacy`: compatibility behavior.
- `UnicodeEscape.Disabled`: treats Unicode escapes according to the unknown-escape policy.
- `UnicodeEscape.FixedWidth(digits)`: fixed-width Unicode escapes.
- `UnicodeEscape.Braced(minDigits, maxDigits)`: braced Unicode escapes.
- `UnknownEscapePolicy.Error`: reject unknown backslash escapes.
- `UnknownEscapePolicy.KeepBackslash`: preserve the backslash and escaped character.
- `UnknownEscapePolicy.DropBackslash`: drop the backslash and keep the escaped character.

## Number Configuration

- `NumberStyle.Json`: strict JSON number grammar.
- `NumberStyle.Json5Like`: looser JavaScript/JSON5-like grammar.
- `NumberStyle.Decimal`: configurable decimal grammar.
- `LeadingZeroPolicy.Json`: JSON-style leading-zero behavior.
- `LeadingZeroPolicy.Allow`: allow leading zeros.
- `LeadingZeroPolicy.Forbid`: forbid multi-digit integer parts that start with zero.
- `ExponentPolicy.Forbid`: disable exponents.
- `ExponentPolicy.Allow`: enable configurable exponent markers and optional signs.

## Text Metrics

- `TextSpanMetrics`: length, newline count, first-line length, last-line length, and CRLF boundary
  flags for captured text.
- `TextSpanMetrics.zero`: metrics for empty text.
- `TextSpanMetrics.of(s)`: compute metrics for a `CharSequence`.
- `TextSpanMetrics.of(buf, start, endExclusive)`: compute metrics for a `CharArray` slice.
- `TextSpanMetrics.plus`: concatenate adjacent metrics in constant time.
