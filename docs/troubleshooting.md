# Troubleshooting

## Parser Loops Never Terminate

Check for `CharInput.EOB` before applying ordinary character predicates:

```kotlin
while (input.current != CharInput.EOB && input.current.isLetter()) {
    input.advance()
}
```

`EOB` is a sentinel returned by `current` and `peek`; it is not source text.

## `readIdentifier` Throws At The Start

`DefaultParsers.readIdentifier` requires the current character to be a letter or `_`.

If identifiers are optional in your grammar, guard the call:

```kotlin
if (input.current.isLetter() || input.current == '_') {
    val identifier = DefaultParsers.readIdentifier(input)
}
```

## `takeExact` Returns False

`takeExact` only consumes when the full literal is available at the current cursor. It does not skip
whitespace first.

```kotlin
DefaultParsers.skipHorizontalSpace(input)
check(input.takeExact("="))
```

Use `ignoreCase = true` only for ASCII-like keywords or when your grammar explicitly wants Kotlin's
case-insensitive character comparison.

## String Parsing Rejects Valid-Looking Text

The selected `StringStyle` controls quote characters, Unicode syntax, and unknown escapes.

Common causes:

- `StringStyle.Json` rejects single quotes.
- `StringStyle.Json` accepts fixed-width Unicode escapes, not braced Unicode escapes.
- `StringStyle.RustLike` accepts braced Unicode escapes, not fixed-width Unicode escapes.
- styles with `UnknownEscapePolicy.Error` reject escapes that are not in `escapes`.
- raw CR or LF is rejected unless `allowMultiline` is true.

Use `StringStyle.PermissiveLegacy` only when you intentionally want the historical loose behavior.

## Number Parsing Stops Early

`readNumber(input, style)` stops before the first character that is not part of the selected decimal
grammar. It does not validate that the next character is a delimiter.

After parsing a number, check the following character if your grammar requires a delimiter:

```kotlin
val number = DefaultParsers.readNumber(input, NumberStyle.Json)
require(input.current == ',' || input.current == ']' || input.current == CharInput.EOB)
```

## Mark Expired

Reader-backed inputs have bounded buffers. A mark can expire if parsing advances far enough that
the marked text is no longer retained.

Practical fixes:

- keep marks short-lived
- increase the input capacity
- use `capture()` for streaming token boundaries when possible
- use `InMemoryCharInput` when the full source is already available and arbitrary reset is needed

`SeekableFileCharInput` can seek back to old checkpoints for `reset(mark)`, but `capture(mark)` still
requires the marked text to remain in the current character buffer.

## Line And Column Look Wrong Around CRLF

The input APIs treat CRLF as one logical newline when possible. A lone CR or lone LF also counts as a
newline. Positions are 1-based for line and column and zero-based for absolute character index.

When presenting diagnostics, prefer the captured `Pos` values over recomputing positions from raw
text in downstream code.
