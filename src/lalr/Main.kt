package lalr

fun processFile(fileName: String) = TODO()

fun printUsage() {
    prinln("Usage: [-v] <GrammarFile>")
}

fun main(args: List<String>) {
    val grammarFile: String? = null

    args.forEach {
        when (it) {
            "-v", "--verbose" -> verbose = true
            else -> {
                if (grammarFile == null) {
                    grammarFile = it
                } else {
                    println("Unrecognised flag \"$it\"")
                    printUsage()
                    return
                }
            }
        }
    }

    if (grammarFile == null) {
        printUsage()
        return
    }

    processFile(grammarFile)
}
