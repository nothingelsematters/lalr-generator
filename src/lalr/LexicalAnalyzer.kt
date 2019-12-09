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

fun generateLexer(name: String, tokens: List<Token>): String {
    println(">> Generating Lexical Analyzer")
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

    return analyzerTemplate.format(name, states.joinToString(separator = ",\n${indent(2)}"))
}

val analyzerTemplate =
    """
    import java.io.InputStream
    import java.io.IOException
    import java.text.ParseException

    public class LexicalException(str: String, pos: Int): ParseException(str, pos)

    public data class State(val terminal: String?, val transitions: Map<Char, Int>)

    data class Token(val name: String, val text: String)

    public class %sLexer(val ins: InputStream) {
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
                curToken = Token("!EOF", "${'$'}")
                return
            }
            val text = StringBuilder()

            while (states[currentState].terminal == null) {
                if (curChar == -1) {
                    throw LexicalException("Unexpected end of input", curPos)
                }

                text.append(curChar.toChar())
                currentState = states[currentState].transitions[curChar.toChar()]
                    ?: throw LexicalException("Unexpected symbol \"${'$'}{curChar.toChar()}\"", curPos)
                nextChar()
            }

            curToken = Token(states[currentState].terminal!!, text.toString())
        }
    }
    """.trimIndent()
