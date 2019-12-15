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

fun Regex.toHardcodeString(): String = pattern.replace("\\", "\\\\").replace("\"", "\\\"")

fun generateLexer(name: String, tokens: List<Token>, header: String): String {
    println(">> Generating Lexical Analyzer")
    val tokenStrings = tokens
        .asSequence()
        .map {
            val reg = it.value.toHardcodeString()
            "InputToken(\"${ it.name }\", Regex(\"$reg\", RegexOption.DOT_MATCHES_ALL), skip = ${ it.skip })"
        }
        .joinToString(separator = ",\n${ indent(2) }")
    val globalRegex = tokens.asSequence().map { "(${it.value.toHardcodeString()})" }.joinToString("|")

    return analyzerTemplate.format(header, name, tokenStrings, globalRegex)
}

val analyzerTemplate =
    """
    %s
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

        private val globalRegex = Regex("%s", RegexOption.DOT_MATCHES_ALL)

        var curPos = 0
            private set

        var curLine = 1
            private set

        var curIndex = 0
            private set


        lateinit var curToken: Token private set

        init {
            nextChar()
        }

        private fun nextChar() {
            curPos++
            curIndex++

            try {
                curChar = ins.read()
            } catch (e: IOException) {
                throw ParseException(e.message, curPos)
            }

            if (curChar != -1 && curChar.toChar() == '\n') {
                curLine++
                curIndex = 1
            }
        }

        public fun nextToken() {
            val text = StringBuilder().append(curChar.toChar())

            if (curChar == -1) {
                curToken = Token("!EOF", "${'$'}")
                return
            }
            val previousLine = curLine
            val previousIndex = curIndex

            while (true) {
                if (text.matches(globalRegex)) {
                    while (curChar != -1 && text.matches(globalRegex)) {
                        nextChar()
                        text.append(curChar.toChar())
                    }

                    val str = text.toString().let { it.substring(0, it.lastIndex) }
                    val matching = states.find { str.matches(it.value) }!!
                    curToken = Token(matching.name, str)
                    if (matching.skip) nextToken()
                    return
                }

                nextChar()
                if (curChar == -1) {
                    throw LexicalException("Unexpected char sequence at ${'$'}previousLine:${'$'}previousIndex", curPos)
                }
                text.append(curChar.toChar())
            }
        }
    }
    """.trimIndent()
