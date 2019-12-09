package lalr

import java.io.File

fun processFile(fileName: String, outputFile: String) = TODO()

fun writeGenerator(
    filePrefix: String,
    name: String,
    tokens: List<Token>,
    ruleList: List<StarterRule>,
    start: String,
    parserHeader: String
) {
    File(filePrefix + "Lexer.kt").printWriter().use {
        it.print(generateLexer(name, tokens))
    }
    File(filePrefix + "Parser.kt").printWriter().use {
        it.print(generateSyntaxAnalyzer(name, ruleList, tokens.asSequence().filterNot(Token::skip).map(Token::name).toSet(), start))
    }
}
