package lalr.lexer

import lalr.*


class LexerException(override val message: String): Exception(message)

data class Token(val name: String, val value: String)

fun generateTokens(tokens: List<Token>): String {
    for ((k, v) in tokens.groupBy(Token::name)) {
        when {
            !k.all { it in 'a'..'z' || it in 'A'..'Z' } -> throw LexerException("token name should consist of letters, got \"$k\"")
            k == "EOF" -> throw LexerException("\"EOF\" token name is not available, what for would you need that?")
            v.size > 1 -> throw LexerException("tokens name collision: ${v.joinToString()}")
        }
    }

    return tokensTemplate
        .format (
            tokens.map { "/* ${it.value} */ ${it.name}" }.joinToString(separator = ",\n|${indent()}")
        )
        .trimMargin("|")
}

data class State(var terminal: Token? = null, val transitions: MutableMap<Char, Int> = hashMapOf<Char, Int>()) {
    override fun toString(): String =
        "State(${terminal?.let { "Token.${it.name}" }}, " +
        "hashMapOf<Char, Int>(${transitions.toList().map { (from, to) -> "\'$from\' to $to" }.joinToString() }))"
}

fun generateLexer(tokens: List<Token>): String {
    val states = ArrayList<State>()
    states.add(State())

    for (tk in tokens) {
        var currentState = 0
        val str = tk.value

        for (i in str.indices) {
            if (states[currentState].terminal != null) {
                throw LexerException("tokens collision: \"$str\" goes through terminal \"${str.substring(0 until i)}\"")
            }

            if (!states[currentState].transitions.containsKey(str[i])) {
                states.add(State())
                states[currentState].transitions[str[i]] = states.lastIndex
            }
            currentState = states[currentState].transitions[str[i]]!!
        }
        states[currentState].terminal = tk
    }

    return analyzerTemplate.format(states.joinToString(separator = ",\n${indent(2)}"))
}



val tokensTemplate =
    """
    |public enum class Token {
    |${indent()}%s,
    |${indent()}/* end of input */ EOF
    |}
    """

val analyzerTemplate =
    """
    import java.io.InputStream
    import java.io.IOException
    import java.text.ParseException

    public class LexicalException(str: String, pos: Int): ParseException(str, pos)

    public data class State(val terminal: Token?, val transitions: Map<Char, Int>)

    public class LexicalAnalyzer(val ins: InputStream) {
        private var curChar = 0

        private val states = listOf(
            %s
        )

        var curPos = 0
            private set

        lateinit var curToken: Token
            private set

        init {
            nextChar()
        }

        private fun nextChar() {
            curPos++
            try {
                curChar = ins.read()
            } catch (e: IOException) {
                throw ParseException(e.message, curPos)
            }
        }

        public fun nextToken() {
            var currentState = 0
            if (curChar == -1) {
                curToken = Token.EOF
                return
            }

            while (states[currentState].terminal == null) {
                if (curChar == -1) {
                    throw LexicalException("Unexpected end of input", curPos)
                }

                currentState = states[currentState].transitions[curChar.toChar()] ?: throw LexicalException("Unexpected symbol", curPos)
                nextChar()
            }

            curToken = states[currentState].terminal!!
        }
    }
    """.trimIndent()
