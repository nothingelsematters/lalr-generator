package lalr

import lalr.input.InputToken
import lalr.input.InputParser
import java.io.File

fun printUsage() {
    println(
        """
        Usage: [-v] [-d <file path>] <GrammarFile>
        -v/--verbose: logging info
        -d/--directory: output file path
        -h/--help: print help
        """.trimIndent()
    )
}

fun processFile(fileName: String, outputFile: String) {
    println("> Parsing Grammar")
    File(fileName).inputStream().use {
        val input = InputParser().parse(it)
        writeGenerator(outputFile, input.name, input.tokens, input.rules, input.rules.first().rule.name, input.header)
    }
}

fun InputToken.toToken(): Token = Token(name, value, skip)

fun main(args: Array<String>) {
    var grammarFile: String? = null
    var outputFile: String? = null

    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "-v", "--verbose" -> verbose = true
            "-d", "--directory" -> {
                outputFile = args[++index]
            }
            "-h", "--help" -> {
                printUsage()
                return
            }
            else -> {
                if (grammarFile == null) {
                    grammarFile = args[index]
                } else {
                    println("Unrecognised flag \"${args[index]}\"")
                    printUsage()
                    return
                }
            }
        }
        index++
    }

    if (grammarFile == null) {
        printUsage()
        return
    }
    if (outputFile == null) {
        outputFile = grammarFile.substringBeforeLast('.')
    }

    try {
        processFile(grammarFile, outputFile)
    } catch (e: Exception) {
        println("ERROR: ${e.message}")
    }
}
