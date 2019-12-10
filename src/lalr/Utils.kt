package lalr

open class ParserGenerationException(val msg: String): Exception(msg)

data class Token(val name: String, val value: Regex, val skip: Boolean = false)

const val START = "!Start"
const val EPSILON = "!EPSILON"
const val EOF = "!EOF"

fun indent(level: Int = 1): String = " ".repeat(4 * level)

var verbose = false

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

fun <T> logMaps(col: List<Map<String, T>>) {
    val indexTab = 4
    val elements = col.asSequence().flatMap { it.keys.asSequence() }.toSet().toList()
    val spaces = elements.map {
        maxOf(3, it.length + 1, col.mapNotNull { m -> m[it]?.toString()?.length }.max()?.plus(1) ?: 3)
    }

    log(" ".repeat(indexTab))
    elements.forEachIndexed { index, it -> log("${" ".repeat(spaces[index] - it.length)}$it") }
    logln()

    col.forEachIndexed { i, m ->
        val str = i.toString()
        log("${" ".repeat(indexTab - str.length)}$str")

        elements.forEachIndexed { j, it ->
            val maxSpace = spaces[j]
            log(
                m[it]
                    ?.let {
                        "${" ".repeat(maxSpace - it.toString().length)}$it"
                    }
                    ?: " ".repeat(maxSpace)
            )
        }
        logln()
    }
}
