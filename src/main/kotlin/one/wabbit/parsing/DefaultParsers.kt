package one.wabbit.parsing

import kotlinx.serialization.Serializable

@Serializable data class Spanned<out Span, out T>(val span: Span, val value: T) {
    fun void(): Spanned<Span, Unit> =
        Spanned(span, Unit)
    fun emptySpan(): Spanned<EmptySpan, T> =
        Spanned(EmptySpan, value)
    fun <R> map(mapper: (T) -> R): Spanned<Span, R> =
        Spanned(span, mapper(value))
    fun <R> mapSpan(mapper: (Span) -> R): Spanned<R, T> =
        Spanned(mapper(span), value)
    fun <U> replace(value: U): Spanned<Span, U> =
        Spanned(span, value)
    fun <U> replaceSpan(span: U): Spanned<U, T> =
        Spanned(span, value)
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

    interface ParseNumberErrors<Span> {
        fun nonDigitsFollowingMinus(input: CharInput<Span>, start: CharInput.Mark): Nothing
        fun nonDigitsFollowingDot(input: CharInput<Span>, start: CharInput.Mark): Nothing
        fun nonDigitsFollowingExp(input: CharInput<Span>, start: CharInput.Mark): Nothing
    }
    fun <Span> readNumber(input: CharInput<Span>, errors: ParseNumberErrors<Span>): Spanned<Span, String> {
        val start = input.mark()
        val sb = StringBuilder()

        if (input.current == '-' || input.current == '+') {
            sb.append(input.current)
            input.advance()
        }

        skipSpaces(input)

        if (input.current == '0') {
            sb.append('0')
            input.advance()
        } else {
            if (input.current !in '1'..'9') {
                // input.fail("Invalid number: ${input.current}")
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
                // input.fail("Invalid number: ${input.current}")
                errors.nonDigitsFollowingDot(input, start)
            }
            while (input.current in '0'..'9') {
                sb.append(input.current)
                input.advance()
            }
        }

        if (input.current == 'e' || input.current == 'E') {
            sb.append('e')
            input.advance()
            if (input.current == '+' || input.current == '-') {
                sb.append(input.current)
                input.advance()
            }
            if (input.current !in '0'..'9') {
                // input.fail("Invalid number: ${input.current}")
                errors.nonDigitsFollowingExp(input, start)
            }
            while (input.current in '0'..'9') {
                sb.append(input.current)
                input.advance()
            }
        }

        return Spanned(input.capture(start), sb.toString())
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

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun <Span> readString(input: CharInput<Span>): Spanned<Span, String> {
        // "\"\"", "\"a\"", "'a'", "\"a b\"", "'abc'", "'\"'",

        require(input.current == '\'' || input.current == '"') {
            "Expected quote while parsing string, found '${input.current}'"
        }
        val quote = input.current
        val start = input.mark()
        input.advance()

        val sb = StringBuilder()
        while (true) {
            if (input.current == quote) {
                input.advance()
                break
            }
            if (input.current == CharInput.EOB) {
                error("Expected '$quote' while parsing string, found end of buffer")
            }
            if (input.current == '\\') {
                input.advance()
                when (input.current) {
                    '\\' -> {
                        sb.append('\\')
                        input.advance()
                    }
                    '\'' -> {
                        sb.append('\'')
                        input.advance()
                    }
                    '"' -> {
                        sb.append('"')
                        input.advance()
                    }
                    'n' -> {
                        sb.append('\n')
                        input.advance()
                    }
                    'r' -> {
                        sb.append('\r')
                        input.advance()
                    }
                    't' -> {
                        sb.append('\t')
                        input.advance()
                    }
                    'u' -> {
                        input.advance()
                        val start = input.mark()
                        if (input.current != '{') {
                            error("Expected '{' after '\\u' while parsing string")
                        }
                        input.advance()
                        var codepoint = 0
                        while (true) {
                            if (input.current == '}') {
                                input.advance()
                                break
                            }
                            if (!input.current.isHexDigit()) {
                                error("Expected hex digit while parsing string")
                            }
                            codepoint = codepoint * 16 + input.current.toString().toInt(16)
                            input.advance()
                        }
                        sb.append(codepoint.toChar())
                    }
                    else -> {
                        sb.append('\\')
                        sb.append(input.current)
                        input.advance()
                    }
                }
            } else {
                sb.append(input.current)
                input.advance()
            }
        }

        val span = input.capture()
        return Spanned(span, sb.toString())
    }
}
