package lalr

import java.io.File


class LexerGenerationException(val errorMessage: String): ParserGenerationException(errorMessage)

data class State(
    var terminal: String? = null,
    val transitions: MutableMap<Char?, Int> = hashMapOf<Char?, Int>(),
    var skip: Boolean = false
) {
    override fun toString(): String =
        StringBuilder()
            .append("State(")
            .append(terminal?.let { "\"$it\"" })
            .append(", hashMapOf<Char?, Int>(")
            .append(transitions.toList().map { (from, to) -> "\'$from\' to $to" }.joinToString())
            .append("), skip = $skip)")
            .toString()
}

fun generateLexer(name: String, tokens: List<Token>): String {
    println(">> Generating Lexical Analyzer")
    val tokenStrings = tokens
        .asSequence()
        .map {
            "InputToken(\"${ it.name }\", Regex(\"${ it.value.pattern.replace("\\", "\\\\") }\"), skip = ${ it.skip })"
        }
        .joinToString(separator = ",\n${ indent(2) }")
    return analyzerTemplate.format(name, tokenStrings)
}

val analyzerTemplate =
    """
    import java.io.InputStream
    import java.io.IOException
    import java.text.ParseException

    public class LexicalException(str: String, pos: Int): ParseException(str, pos)

    public data class InputToken(val name: String, val value: Regex, val skip: Boolean = false)

    data class Token(val name: String, val text: String)

    public class %sLexer(val ins: InputStream) {
        private var curChar = 0

        private val states = listOf<InputToken>(
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
            val text = StringBuilder().append(curChar.toChar())

            if (curChar == -1) {
                curToken = Token("!EOF", "${'$'}")
                return
            }

            while (true) {
                states.forEach {
                    if (text.toString().matches(it.value)) {
                        while (curChar != -1 && text.toString().matches(it.value)) {
                            nextChar()
                            text.append(curChar.toChar())
                        }
                        curToken = Token(it.name, text.toString().let { it.substring(0, it.lastIndex) })
                        return
                    }
                }

                nextChar()
                if (curChar == -1) {
                    throw LexicalException("Unexpected char sequence", curPos)
                }
            }
        }
    }
    """.trimIndent()
