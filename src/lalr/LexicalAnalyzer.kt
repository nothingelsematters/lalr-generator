package lalr

import java.io.File


class LexerGenerationException(val errorMessage: String): ParserGenerationException(errorMessage)

data class State(var terminal: String? = null, val transitions: MutableMap<Char, Int> = hashMapOf<Char, Int>()) {
    override fun toString(): String =
        StringBuilder()
            .append("State(")
            .append(terminal?.let { "\"$it\"" })
            .append(", ")
            .append("hashMapOf<Char, Int>(")
            .append(transitions.toList().map { (from, to) -> "\'$from\' to $to" }.joinToString())
            .append("))")
            .toString()
}

fun generateLexer(tokens: List<Token>): String {
    println(">> Generating Syntax Analyzer")
    val states = ArrayList<State>()
    states.add(State())

    for (tk in tokens) {
        var currentState = 0
        val str = tk.value

        for (i in str.indices) {
            if (states[currentState].terminal != null) {
                throw LexerGenerationException("tokens collision: \"$str\" goes through terminal \"${str.substring(0 until i)}\"")
            }

            if (!states[currentState].transitions.containsKey(str[i])) {
                states.add(State())
                states[currentState].transitions[str[i]] = states.lastIndex
            }
            currentState = states[currentState].transitions[str[i]]!!
        }
        states[currentState].terminal = tk.name
    }

    return analyzerTemplate.format(states.joinToString(separator = ",\n${indent(2)}"))
}

val analyzerTemplate =
    """
    import java.io.InputStream
    import java.io.IOException
    import java.text.ParseException

    public class LexicalException(str: String, pos: Int): ParseException(str, pos)

    public data class State(val terminal: String?, val transitions: Map<Char, Int>)

    public class LexicalAnalyzer(val ins: InputStream) {
        private var curChar = 0

        private val states = listOf(
            %s
        )

        var curPos = 0
            private set

        lateinit var curToken: String
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
                curToken = "!EOF"
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
