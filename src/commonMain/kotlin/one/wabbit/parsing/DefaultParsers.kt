// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.parsing

import kotlinx.serialization.Serializable

/**
 * Parsed value together with the span of input that produced it.
 *
 * @param Span span type captured from the input.
 * @param T parsed value type.
 * @property span captured source span.
 * @property value parsed value.
 */
@Serializable
data class Spanned<out Span, out T>(val span: Span, val value: T) {
    /** Replace [value] with [Unit] while keeping the original [span]. */
    fun void(): Spanned<Span, Unit> = Spanned(span, Unit)

    /** Drop source-location data by replacing [span] with [EmptySpan]. */
    fun emptySpan(): Spanned<EmptySpan, T> = Spanned(EmptySpan, value)

    /** Transform [value] while keeping the original [span]. */
    fun <R> map(mapper: (T) -> R): Spanned<Span, R> = Spanned(span, mapper(value))

    /** Transform [span] while keeping the original [value]. */
    fun <R> mapSpan(mapper: (Span) -> R): Spanned<R, T> = Spanned(mapper(span), value)

    /** Replace [value] while keeping the original [span]. */
    fun <U> replace(value: U): Spanned<Span, U> = Spanned(span, value)

    /** Replace [span] while keeping the original [value]. */
    fun <U> replaceSpan(span: U): Spanned<U, T> = Spanned(span, value)
}

/* ============================
 * STRING SYNTAX CONFIG
 * ============================
 */

/** Unicode escape syntax accepted by [DefaultParsers.readString]. */
@Serializable
sealed interface UnicodeEscape {
    /** Reject `\u` escapes unless the string style treats them as unknown escapes. */
    @Serializable data object Disabled : UnicodeEscape

    /**
     * Fixed-width `\uXXXX` escapes with exactly [digits] hexadecimal digits.
     *
     * This is the Unicode form used by JSON, Java strings, and C-like string syntaxes.
     *
     * @property digits required number of hexadecimal digits.
     */
    @Serializable data class FixedWidth(val digits: Int = 4) : UnicodeEscape

    /**
     * Braced `\u{...}` escapes with [minDigits] to [maxDigits] hexadecimal digits.
     *
     * This is the Unicode form used by Rust-like syntaxes.
     *
     * @property minDigits minimum number of hexadecimal digits required inside the braces.
     * @property maxDigits maximum number of hexadecimal digits allowed inside the braces.
     */
    @Serializable data class Braced(val minDigits: Int = 1, val maxDigits: Int = 6) : UnicodeEscape
}

/** Policy for backslash escapes that are not listed in [StringStyle.escapes]. */
@Serializable
enum class UnknownEscapePolicy {
    /** Throw on any unknown escape. */
    Error,

    /** Keep the backslash and the char verbatim (legacy DefaultParsers.readString). */
    KeepBackslash,

    /** Drop the backslash, keep the following char. */
    DropBackslash,
}

/** Configures [DefaultParsers.readString] quoting, escape, and newline behavior. */
@Serializable
sealed interface StringStyle {
    /** Whether `'...'` string literals are accepted. */
    val allowSingleQuotes: Boolean

    /** Whether `"..."` string literals are accepted. */
    val allowDoubleQuotes: Boolean

    /** Whether raw CR or LF characters may appear before the closing quote. */
    val allowMultiline: Boolean

    /** Unicode escape syntax accepted after `\u`. */
    val unicode: UnicodeEscape

    /** Behavior for unknown backslash escapes. */
    val unknownEscape: UnknownEscapePolicy

    /** Simple single-character escapes such as `n` to newline. */
    val escapes: Map<Char, Char>

    // ---- Presets ----

    /**
     * Backward-compatible default used by [DefaultParsers.readString].
     *
     * This style accepts single and double quotes, braced Unicode escapes, and unknown escapes
     * preserved with their leading backslash.
     */
    @Serializable
    data object PermissiveLegacy : StringStyle {
        // - both quotes allowed
        // - supports \', \", \\, \n, \r, \t
        // - supports \u{...} (1..6 hex digits)
        // - unknown escapes are kept literally with the backslash
        override val allowSingleQuotes = true
        override val allowDoubleQuotes = true
        override val allowMultiline = false
        override val unicode: UnicodeEscape = UnicodeEscape.Braced(1, 6)
        override val unknownEscape = UnknownEscapePolicy.KeepBackslash
        override val escapes: Map<Char, Char> =
            mapOf('\\' to '\\', '\'' to '\'', '"' to '"', 'n' to '\n', 'r' to '\r', 't' to '\t')
    }

    /**
     * Strict JSON string syntax.
     *
     * This style allows double-quoted strings, fixed-width Unicode escapes, and JSON's standard
     * single-character escape set.
     */
    @Serializable
    data object Json : StringStyle {
        // Strict JSON (RFC 8259).
        override val allowSingleQuotes = false
        override val allowDoubleQuotes = true
        override val allowMultiline = false
        override val unicode: UnicodeEscape = UnicodeEscape.FixedWidth(4)
        override val unknownEscape = UnknownEscapePolicy.Error
        override val escapes: Map<Char, Char> =
            mapOf(
                '\\' to '\\',
                '"' to '"',
                '/' to '/',
                'b' to '\b',
                'f' to '\u000C',
                'n' to '\n',
                'r' to '\r',
                't' to '\t',
            )
    }

    /**
     * JSON5-like string syntax.
     *
     * This style accepts single quotes in addition to JSON's double quotes. It does not permit raw
     * multiline string content.
     */
    @Serializable
    data object Json5Like : StringStyle {
        // Looser JSON5-ish; single quotes OK; still no raw newlines inside.
        override val allowSingleQuotes = true
        override val allowDoubleQuotes = true
        override val allowMultiline = false
        override val unicode: UnicodeEscape = UnicodeEscape.FixedWidth(4)
        override val unknownEscape = UnknownEscapePolicy.Error
        override val escapes: Map<Char, Char> = Json.escapes
    }

    /** Java/C-style double-quoted string syntax with fixed-width Unicode escapes. */
    @Serializable
    data object JavaCStyle : StringStyle {
        // Classic Java/C escape set with \uXXXX; single quotes not for strings.
        override val allowSingleQuotes = false
        override val allowDoubleQuotes = true
        override val allowMultiline = false
        override val unicode: UnicodeEscape = UnicodeEscape.FixedWidth(4)
        override val unknownEscape = UnknownEscapePolicy.Error
        override val escapes: Map<Char, Char> =
            mapOf(
                '\\' to '\\',
                '"' to '"',
                'b' to '\b',
                'f' to '\u000C',
                'n' to '\n',
                'r' to '\r',
                't' to '\t',
            )
    }

    /** Rust-like double-quoted string syntax with braced Unicode escapes. */
    @Serializable
    data object RustLike : StringStyle {
        // Double quotes; \u{...}; conservative on unknown escapes.
        override val allowSingleQuotes = false
        override val allowDoubleQuotes = true
        override val allowMultiline = false
        override val unicode: UnicodeEscape = UnicodeEscape.Braced(1, 6)
        override val unknownEscape = UnknownEscapePolicy.Error
        override val escapes: Map<Char, Char> =
            mapOf('\\' to '\\', '"' to '"', 'n' to '\n', 'r' to '\r', 't' to '\t')
    }
}

/* ============================
 * NUMBER SYNTAX CONFIG
 * ============================
 */

/** Policy for integer parts that begin with `0`. */
@Serializable
enum class LeadingZeroPolicy {
    /** JSON-like: integer part is "0" or non-zero followed by digits; "012" is invalid. */
    Json,

    /** Allow any number of leading zeros (e.g., "00042"). */
    Allow,

    /** Forbid any leading zero when more than one digit (only "0" is allowed). */
    Forbid,
}

/** Exponent syntax accepted by [DefaultParsers.readNumber]. */
@Serializable
sealed interface ExponentPolicy {
    /** Do not accept exponent suffixes. */
    @Serializable data object Forbid : ExponentPolicy

    /**
     * Accept exponent suffixes using [letters].
     *
     * @property letters exponent marker characters, commonly `e` and `E`.
     * @property allowSign whether a `+` or `-` may appear after the exponent marker.
     */
    @Serializable
    data class Allow(val letters: Set<Char> = setOf('e', 'E'), val allowSign: Boolean = true) :
        ExponentPolicy
}

/** Configures [DefaultParsers.readNumber] decimal-number grammar. */
@Serializable
sealed interface NumberStyle {
    /**
     * Configurable decimal number syntax.
     *
     * @property allowPlus whether a leading `+` sign is accepted.
     * @property allowMinus whether a leading `-` sign is accepted.
     * @property allowLeadingDot whether numbers may start with `.` before fractional digits.
     * @property allowTrailingDot whether numbers may end with `.` after integer digits.
     * @property leadingZeroPolicy policy for integer parts that start with `0`.
     * @property exponent exponent suffix policy.
     * @property allowUnderscores whether underscores may separate digits.
     */
    @Serializable
    data class Decimal(
        val allowPlus: Boolean = false,
        val allowMinus: Boolean = true,
        val allowLeadingDot: Boolean = false,
        val allowTrailingDot: Boolean = false,
        val leadingZeroPolicy: LeadingZeroPolicy = LeadingZeroPolicy.Json,
        val exponent: ExponentPolicy = ExponentPolicy.Allow(),
        val allowUnderscores: Boolean = false,
    ) : NumberStyle

    /** Strict JSON number grammar. */
    @Serializable data object Json : NumberStyle

    /** Looser JavaScript/JSON5-like grammar with `+`, leading/trailing dots, and underscores. */
    @Serializable data object Json5Like : NumberStyle

    // A legacy shim is intentionally omitted; the old API remains as-is.
}

internal fun NumberStyle.asDecimal(): NumberStyle.Decimal =
    when (this) {
        is NumberStyle.Decimal -> this
        NumberStyle.Json ->
            NumberStyle.Decimal(
                allowPlus = false,
                allowMinus = true,
                allowLeadingDot = false,
                allowTrailingDot = false,
                leadingZeroPolicy = LeadingZeroPolicy.Json,
                exponent = ExponentPolicy.Allow(),
                allowUnderscores = false,
            )
        NumberStyle.Json5Like ->
            NumberStyle.Decimal(
                allowPlus = true,
                allowMinus = true,
                allowLeadingDot = true,
                allowTrailingDot = true,
                leadingZeroPolicy = LeadingZeroPolicy.Allow,
                exponent = ExponentPolicy.Allow(),
                allowUnderscores = true,
            )
    }

/**
 * Small default token parsers for consumers that do not need a full parser-combinator framework.
 *
 * The functions operate directly on [CharInput], advance it on success, and return [Spanned] values
 * when they produce a token. They intentionally validate only their own token grammar; a caller is
 * responsible for checking delimiters and higher-level syntax.
 */
object DefaultParsers {
    /**
     * Consume all Kotlin-whitespace characters from [input].
     *
     * @param input input to consume.
     * @param start mark used as the beginning of the returned span.
     * @return span covering all consumed whitespace.
     */
    fun <Span> skipSpaces(input: CharInput<Span>, start: CharInput.Mark = input.mark()): Span {
        while (true) {
            val char = input.current
            when {
                char == CharInput.EOB -> return input.capture(start)
                char.isWhitespace() -> input.advance()
                else -> return input.capture(start)
            }
        }
    }

    /**
     * Consume horizontal spaces from [input].
     *
     * Only ASCII space and tab are consumed. CR and LF are left for the caller.
     *
     * @param input input to consume.
     * @param start mark used as the beginning of the returned span.
     * @return span covering all consumed horizontal whitespace.
     */
    fun <Span> skipHorizontalSpace(
        input: CharInput<Span>,
        start: CharInput.Mark = input.mark(),
    ): Span {
        while (true) {
            val c = input.current
            if (c == ' ' || c == '\t') input.advance() else return input.capture(start)
        }
    }

    /** Return whether [c] is a CR or LF line terminator. */
    fun isEol(c: Char): Boolean = (c == '\n' || c == '\r')

    /**
     * Advance [input] until CR, LF, or [CharInput.EOB].
     *
     * The line terminator itself is not consumed.
     */
    fun <Span> skipToEol(input: CharInput<Span>) {
        while (input.current != CharInput.EOB && !isEol(input.current)) input.advance()
    }

    /**
     * Read an identifier made of letters, digits, and underscores.
     *
     * The current character must be a letter or `_`; following characters may also be digits.
     *
     * @throws IllegalArgumentException when the current character cannot start an identifier.
     */
    fun <Span> readIdentifier(input: CharInput<Span>): Spanned<Span, String> {
        require(input.current.isLetter() || input.current == '_')
        val start = input.mark()
        val sb = StringBuilder()
        sb.append(input.current)
        input.advance()
        while (input.current.isLetterOrDigit() || input.current == '_') {
            sb.append(input.current)
            input.advance()
        }
        return Spanned(input.capture(start), sb.toString())
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /* ============================
     * CONFIGURABLE STRING PARSER
     * ============================
     */

    /**
     * Read a quoted string according to [style].
     *
     * The returned value is unescaped. The returned span covers the complete source literal,
     * including the opening and closing quotes.
     *
     * @throws IllegalArgumentException when the opening quote is not allowed by [style], a
     *   malformed Unicode escape is found, or the produced code point is invalid.
     * @throws IllegalStateException when the literal is unterminated, contains an unexpected raw
     *   newline, or contains an unknown escape rejected by [style].
     */
    fun <Span> readString(input: CharInput<Span>, style: StringStyle): Spanned<Span, String> {
        fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

        fun readFixedUnicode(digits: Int): Int {
            var cp = 0
            repeat(digits) { i ->
                val c = input.current
                require(c != CharInput.EOB && c.isHexDigit()) {
                    "Expected hex digit in \\u escape (position $i/$digits)"
                }
                cp = (cp shl 4) or c.digitToInt(16)
                input.advance()
            }
            return cp
        }

        fun readBracedUnicode(min: Int, max: Int): Int {
            var cp = 0
            var count = 0
            require(input.current == '{') { "Expected '{' after \\u" }
            input.advance()
            while (true) {
                val c = input.current
                when {
                    c == CharInput.EOB -> error("Unterminated \\u{...} escape")
                    c == '}' -> {
                        input.advance()
                        break
                    }
                    c.isHexDigit() -> {
                        require(count < max) { "Too many hex digits in \\u{...}; max=$max" }
                        cp = (cp shl 4) or c.digitToInt(16)
                        input.advance()
                        count++
                    }
                    else -> error("Expected hex digit in \\u{...}")
                }
            }
            require(count in min..max) {
                "Unicode escape must have $min..$max hex digits; got $count"
            }
            return cp
        }

        fun appendCodePoint(sb: StringBuilder, codepoint: Int) {
            require(codepoint in 0..0x10FFFF) { "Code point out of range: $codepoint" }
            require(codepoint !in 0xD800..0xDFFF) { "Invalid surrogate code point: $codepoint" }
            if (codepoint <= 0xFFFF) {
                sb.append(codepoint.toChar())
            } else {
                val high = ((codepoint - 0x10000) shr 10) + 0xD800
                val low = ((codepoint - 0x10000) and 0x3FF) + 0xDC00
                sb.append(high.toChar())
                sb.append(low.toChar())
            }
        }

        // Choose quote
        require(input.current == '\'' || input.current == '"') {
            "Expected quote while parsing string, found '${input.current}'"
        }
        val quote = input.current
        require(
            (quote == '"' && style.allowDoubleQuotes) || (quote == '\'' && style.allowSingleQuotes)
        ) {
            "Quote character '$quote' not allowed by style"
        }

        val start = input.mark()
        input.advance()

        val sb = StringBuilder()
        while (true) {
            val c = input.current
            when (c) {
                CharInput.EOB -> error("Unterminated string literal")
                '\n',
                '\r' -> {
                    if (!style.allowMultiline) error("Newline in string literal")
                    // If you decide to normalize CRLF to LF, do it here; currently we keep raw.
                    sb.append(c)
                    input.advance()
                }
                '\\' -> {
                    input.advance()
                    val esc = input.current
                    when {
                        esc == CharInput.EOB -> error("Unterminated backslash escape")
                        esc == 'u' -> {
                            input.advance()
                            when (val u = style.unicode) {
                                is UnicodeEscape.Disabled ->
                                    when (style.unknownEscape) {
                                        UnknownEscapePolicy.Error ->
                                            error("Unicode escapes disabled")
                                        UnknownEscapePolicy.KeepBackslash -> {
                                            sb.append('\\')
                                                .append('u') // fallthrough keeps next char path
                                        }
                                        UnknownEscapePolicy.DropBackslash -> {
                                            sb.append('u')
                                        }
                                    }
                                is UnicodeEscape.FixedWidth -> {
                                    val cp = readFixedUnicode(u.digits)
                                    appendCodePoint(sb, cp)
                                }
                                is UnicodeEscape.Braced -> {
                                    val cp = readBracedUnicode(u.minDigits, u.maxDigits)
                                    appendCodePoint(sb, cp)
                                }
                            }
                        }
                        style.escapes.containsKey(esc) -> {
                            sb.append(style.escapes.getValue(esc))
                            input.advance()
                        }
                        else -> {
                            when (style.unknownEscape) {
                                UnknownEscapePolicy.Error -> error("Unknown escape: \\$esc")
                                UnknownEscapePolicy.KeepBackslash -> {
                                    sb.append('\\')
                                    sb.append(esc)
                                    input.advance()
                                }
                                UnknownEscapePolicy.DropBackslash -> {
                                    sb.append(esc)
                                    input.advance()
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (c == quote) {
                        input.advance()
                        break
                    }
                    sb.append(c)
                    input.advance()
                }
            }
        }
        return Spanned(input.capture(start), sb.toString())
    }

    /**
     * Read a quoted string using [StringStyle.PermissiveLegacy].
     *
     * This overload is kept for compatibility with the original parser behavior.
     */
    fun <Span> readString(input: CharInput<Span>): Spanned<Span, String> =
        readString(input, StringStyle.PermissiveLegacy)

    /* ============================
     * CONFIGURABLE NUMBER PARSER
     * ============================
     */

    /**
     * Read a decimal number according to [style].
     *
     * The returned value is the exact source text of the number, including signs, decimal point,
     * exponent, and underscores accepted by the style. The function stops before the first
     * non-number character.
     *
     * @throws IllegalArgumentException when the source violates the selected style.
     * @throws IllegalStateException when the current character cannot start a number.
     */
    fun <Span> readNumber(input: CharInput<Span>, style: NumberStyle): Spanned<Span, String> {
        val cfg = style.asDecimal()

        fun isDigit(c: Char) = c in '0'..'9'

        fun isExpLetter(c: Char) =
            (cfg.exponent as? ExponentPolicy.Allow)?.letters?.contains(c) == true

        val start = input.mark()
        val sb = StringBuilder()

        // Sign
        if (input.current == '+' || input.current == '-') {
            val s = input.current
            when (s) {
                '+' -> require(cfg.allowPlus) { "Leading '+' not allowed" }
                '-' -> require(cfg.allowMinus) { "Leading '-' not allowed" }
            }
            sb.append(s)
            input.advance()
        }

        var sawIntDigits = 0
        var sawDot = false
        var sawFracDigits = 0

        fun readDigits(): Int {
            var count = 0
            var prevUnderscore = false
            while (true) {
                val c = input.current
                when {
                    isDigit(c) -> {
                        sb.append(c)
                        input.advance()
                        count++
                        prevUnderscore = false
                    }
                    cfg.allowUnderscores && c == '_' -> {
                        val next = input.peek(1)
                        require(count > 0 && isDigit(next)) { "Invalid underscore placement" }
                        sb.append('_')
                        input.advance()
                        prevUnderscore = true
                    }
                    else -> {
                        require(!prevUnderscore) { "Number cannot end with '_'" }
                        return count
                    }
                }
            }
        }

        // Integer / leading dot
        when (val c0 = input.current) {
            '.' -> {
                require(cfg.allowLeadingDot) { "Leading '.' not allowed" }
                sb.append('.')
                input.advance()
                sawDot = true
                sawFracDigits = readDigits()
                require(sawFracDigits > 0 || cfg.allowTrailingDot) { "Digits required after '.'" }
            }
            in '0'..'9' -> {
                if (c0 == '0') {
                    sb.append('0')
                    input.advance()
                    sawIntDigits = 1
                    when (cfg.leadingZeroPolicy) {
                        LeadingZeroPolicy.Allow -> {
                            // If more digits follow, read them (e.g., 00042); underscores observed.
                            if (
                                isDigit(input.current) ||
                                    (cfg.allowUnderscores && input.current == '_')
                            ) {
                                sawIntDigits += readDigits()
                            }
                        }
                        LeadingZeroPolicy.Json -> {
                            // "0" is the integer part; next must not be a digit.
                            require(!isDigit(input.current)) { "Leading zeros not allowed" }
                        }
                        LeadingZeroPolicy.Forbid -> {
                            // Single zero is allowed only if the entire integer is exactly "0"
                            require(!isDigit(input.current)) { "Leading zero not allowed" }
                        }
                    }
                } else {
                    sawIntDigits = readDigits() + 0 // read loop consumes from current
                }
            }
            else -> error("Invalid number start: '$c0'")
        }

        // Fraction
        if (!sawDot && input.current == '.') {
            sb.append('.')
            input.advance()
            sawDot = true
            sawFracDigits = readDigits()
            require(sawFracDigits > 0 || cfg.allowTrailingDot) { "Digits required after '.'" }
        }

        // Exponent
        if (cfg.exponent is ExponentPolicy.Allow && isExpLetter(input.current)) {
            val e = input.current
            sb.append(e)
            input.advance()
            if (cfg.exponent.allowSign && (input.current == '+' || input.current == '-')) {
                sb.append(input.current)
                input.advance()
            }
            val expDigits = readDigits()
            require(expDigits > 0) { "Exponent requires at least one digit" }
        }

        return Spanned(input.capture(start), sb.toString())
    }

    /**
     * Error callbacks used by the backward-compatible numeric parser.
     *
     * Each callback receives the input and the start mark for the number currently being parsed.
     * Implementations usually throw a domain-specific parse error.
     */
    interface ParseNumberErrors<Span> {
        /** Report that no digit followed an initial sign or integer position. */
        fun nonDigitsFollowingMinus(input: CharInput<Span>, start: CharInput.Mark): Nothing

        /** Report that no digit followed a decimal point. */
        fun nonDigitsFollowingDot(input: CharInput<Span>, start: CharInput.Mark): Nothing

        /** Report that no digit followed an exponent marker. */
        fun nonDigitsFollowingExp(input: CharInput<Span>, start: CharInput.Mark): Nothing
    }

    /**
     * Read a number using the original parser behavior and external error callbacks.
     *
     * This overload is retained for source compatibility. New code should prefer [readNumber] with
     * an explicit [NumberStyle].
     */
    fun <Span> readNumber(
        input: CharInput<Span>,
        errors: ParseNumberErrors<Span>,
    ): Spanned<Span, String> {
        // ORIGINAL IMPLEMENTATION — left intact for compatibility.
        val start = input.mark()
        val sb = StringBuilder()

        if (input.current == '-' || input.current == '+') {
            sb.append(input.current)
            input.advance()
        }

        if (input.current == '0') {
            sb.append('0')
            input.advance()
        } else {
            if (input.current !in '1'..'9') {
                errors.nonDigitsFollowingMinus(input, start)
            }
            while (input.current in '0'..'9') {
                sb.append(input.current)
                input.advance()
            }
        }

        if (input.current == '.') {
            sb.append('.')
            input.advance()
            if (input.current !in '0'..'9') {
                errors.nonDigitsFollowingDot(input, start)
            }
            while (input.current in '0'..'9') {
                sb.append(input.current)
                input.advance()
            }
        }

        if (input.current == 'e' || input.current == 'E') {
            val expChar = input.current
            sb.append(expChar)
            input.advance()
            if (input.current == '+' || input.current == '-') {
                sb.append(input.current)
                input.advance()
            }
            if (input.current !in '0'..'9') {
                errors.nonDigitsFollowingExp(input, start)
            }
            while (input.current in '0'..'9') {
                sb.append(input.current)
                input.advance()
            }
        }

        return Spanned(input.capture(start), sb.toString())
    }
}
