package lalr

import java.io.File
import java.nio.file.Files.createDirectories
import java.nio.file.Paths


fun writeGenerator(
    filePrefix: String,
    name: String,
    tokens: List<Token>,
    ruleList: List<StarterRule>,
    start: String,
    header: String
) {
    writeFile(filePrefix + "Lexer.kt", generateLexer(name, tokens, header))
    writeFile(filePrefix + "Parser.kt", generateSyntaxAnalyzer(name, ruleList,
        tokens.asSequence().filterNot(Token::skip).map(Token::name).toSet(), start, header))
}

fun writeFile(name: String, content: String) {
    println("> Writing $name")
    createDirectories(Paths.get(".", name).getParent())

    File(name).printWriter().use {
        it.print(content)
    }
}
