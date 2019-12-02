package lalr

open class ParserGenerationException(val msg: String): Exception(msg)

fun indent(level: Int = 1): String = " ".repeat(4 * level)

data class Token(val name: String, val value: String)
