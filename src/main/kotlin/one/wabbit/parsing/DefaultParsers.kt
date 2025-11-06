package one.wabbit.parsing

import kotlinx.serialization.Serializable

@Serializable
data class Spanned<out Span, out T>(val span: Span, val value: T) {
    fun void(): Spanned<Span, Unit> = Spanned(span, Unit)

    fun emptySpan(): Spanned<EmptySpan, T> = Spanned(EmptySpan, value)

    fun <R> map(mapper: (T) -> R): Spanned<Span, R> = Spanned(span, mapper(value))

    fun <R> mapSpan(mapper: (Span) -> R): Spanned<R, T> = Spanned(mapper(span), value)

    fun <U> replace(value: U): Spanned<Span, U> = Spanned(span, value)

    fun <U> replaceSpan(span: U): Spanned<U, T> = Spanned(span, value)
}

/* ============================
 * STRING SYNTAX CONFIG
 * ============================
 */

@Serializable
sealed interface UnicodeEscape {
    @Serializable data object Disabled : UnicodeEscape

    /** \uXXXX with exactly [digits] hex digits (typical Java/C/JSON). */
    @Serializable data class FixedWidth(val digits: Int = 4) : UnicodeEscape

    /** \u{...} with \[minDigits..maxDigits\] hex digits (typical Rust-like). */
    @Serializable data class Braced(val minDigits: Int = 1, val maxDigits: Int = 6) : UnicodeEscape
}

@Serializable
enum class UnknownEscapePolicy {
    /** Throw on any unknown escape. */
    Error,

    /** Keep the backslash and the char verbatim (legacy DefaultParsers.readString). */
    KeepBackslash,

    /** Drop the backslash, keep the following char. */
    DropBackslash,
}

@Serializable
sealed interface StringStyle {
    val allowSingleQuotes: Boolean
    val allowDoubleQuotes: Boolean
    val allowMultiline: Boolean
    val unicode: UnicodeEscape
    val unknownEscape: UnknownEscapePolicy

    /** Simple single-char escapes like 'n' -> '\n'. */
    val escapes: Map<Char, Char>

    // ---- Presets ----

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

@Serializable
enum class LeadingZeroPolicy {
    /** JSON-like: integer part is "0" or non-zero followed by digits; "012" is invalid. */
    Json,

    /** Allow any number of leading zeros (e.g., "00042"). */
    Allow,

    /** Forbid any leading zero when more than one digit (only "0" is allowed). */
    Forbid,
}

@Serializable
sealed interface ExponentPolicy {
    @Serializable data object Forbid : ExponentPolicy

    @Serializable
    data class Allow(val letters: Set<Char> = setOf('e', 'E'), val allowSign: Boolean = true) :
        ExponentPolicy
}

@Serializable
sealed interface NumberStyle {
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

    /** A looser JS/JSON5-ish grammar: +, leading/trailing dot, underscores. */
    @Serializable data object Json5Like : NumberStyle

    /** A “legacy” shim is intentionally omitted—old API remains as-is. */
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

object DefaultParsers {
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

    /** Skip only horizontal spaces (space or tab). Does not consume newlines. */
    fun <Span> skipHorizontalSpace(
        input: CharInput<Span>,
        start: CharInput.Mark = input.mark(),
    ): Span {
        while (true) {
            val c = input.current
            if (c == ' ' || c == '\t') input.advance() else return input.capture(start)
        }
    }

    /** True if char is CR or LF. */
    fun isEol(c: Char): Boolean = (c == '\n' || c == '\r')

    /** Advance until end-of-line (not consuming the newline). */
    fun <Span> skipToEol(input: CharInput<Span>) {
        while (input.current != CharInput.EOB && !isEol(input.current)) input.advance()
    }

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
            sb.append(Character.toChars(codepoint))
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

    /** Backward-compatible: retains previous behavior (PermissiveLegacy). */
    fun <Span> readString(input: CharInput<Span>): Spanned<Span, String> =
        readString(input, StringStyle.PermissiveLegacy)

    /* ============================
     * CONFIGURABLE NUMBER PARSER
     * ============================
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

    /** Backward-compatible numeric parser (unchanged behavior). */
    interface ParseNumberErrors<Span> {
        fun nonDigitsFollowingMinus(input: CharInput<Span>, start: CharInput.Mark): Nothing

        fun nonDigitsFollowingDot(input: CharInput<Span>, start: CharInput.Mark): Nothing

        fun nonDigitsFollowingExp(input: CharInput<Span>, start: CharInput.Mark): Nothing
    }

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
