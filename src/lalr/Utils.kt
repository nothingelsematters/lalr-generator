package lalr

open class ParserGenerationException(val msg: String): Exception(msg)

data class Token(val name: String, val value: String)

const val START = "!Start"
const val EPSILON = "!EPSILON"
const val EOF = "!EOF"

fun indent(level: Int = 1): String = " ".repeat(4 * level)

var verbose = true

fun log(info: List<Any>) {
    if (verbose) {
        info.forEach(::print)
    }
}

fun log(vararg info: Any) {
    log(info.asList())
}

fun logln(vararg info: Any) {
    log(info.asList() + "\n")
}
