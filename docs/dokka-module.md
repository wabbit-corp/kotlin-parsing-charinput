# Module kotlin-parsing-charinput

`kotlin-parsing-charinput` provides low-level character input primitives for parser authors.

The module centers on `CharInput`, a mutable cursor with current-character access, lookahead,
mark/reset, line/column tracking, and span capture. It also includes built-in span shapes and a
small `DefaultParsers` helper object for common tokens.

## What It Supports

- in-memory character input on all supported Kotlin Multiplatform targets
- JVM/Android reader-backed streaming inputs
- JVM/Android seekable file input with checkpoints
- source positions using line, column, and absolute character index
- captured spans with no data, positions only, text only, or text plus positions
- configurable quoted string parsing
- configurable decimal number parsing
- identifier and whitespace helpers

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-parsing-charinput:1.2.0")
}
```

## Quick Start

```kotlin
import one.wabbit.parsing.CharInput
import one.wabbit.parsing.DefaultParsers
import one.wabbit.parsing.StringStyle

val input = CharInput.withTextAndPosSpans("name = \"wabbit\"")

val name = DefaultParsers.readIdentifier(input)
DefaultParsers.skipHorizontalSpace(input)
check(input.takeExact("="))
DefaultParsers.skipHorizontalSpace(input)
val value = DefaultParsers.readString(input, StringStyle.Json)

check(name.value == "name")
check(value.value == "wabbit")
```

## API Notes

- `CharInput.EOB` is the end-of-buffer sentinel returned by `current` and `peek`.
- `mark()` and `reset(mark)` support speculative parsing.
- `capture(mark)` returns a span from a specific mark to the current cursor.
- `capture()` returns a span from the previous implicit capture boundary to the current cursor and
  advances that boundary to the current cursor.
- JVM/Android reader and file inputs may reject marks or captures whose buffered text has expired.
