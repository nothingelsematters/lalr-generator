package lalr

fun generateParser(name: String, tokens: List<Token>, ruleList: List<Rule>, start: String): String {
    println("> Generating Parser")
    generateLexer(tokens)
    generateSyntaxAnalyzer(name, ruleList, tokens.asSequence().map(Token::name).toSet(), start)
    return TODO()
}
