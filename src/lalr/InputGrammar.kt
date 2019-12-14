package lalr

data class Input(val name: String, val header: String, val rules: List<StarterRule>, val tokens: List<Token>)

fun main() {
    val tokens = listOf(
        // skip whitespaces
        Token("WHITESPACES", Regex("[ \\n\\t]+"), true),
        // symbols
        Token("OPEN_BRACKET", Regex("\\[")),
        Token("CLOSE_BRACKET", Regex("\\]")),
        Token("OPEN_TRIANGULAR", Regex("<")),
        Token("CLOSE_TRIANGULAR", Regex(">")),
        Token("SEMICOLON", Regex(";")),
        Token("COLON", Regex(":")),
        Token("OR", Regex("\\|")),
        Token("COMA", Regex(",")),
        // grammar words
        Token("HEADER", Regex("@header")),
        Token("RETURNS", Regex("@returns")),
        Token("GRAMMAR", Regex("@grammar")),
        // huge strings
        Token("REGEX", Regex("\".*\"")),
        Token("NAME", Regex("[a-zA-Z][a-zA-Z_]*")),
        Token("CODE", Regex("\\{.*?\\}", RegexOption.DOT_MATCHES_ALL))
    )

    /*
        start -> input
        input -> grammar header rules
        input -> grammar rules
        grammar -> GRAMMAR NAME SEMICOLON
        header -> HEADER CODE
        rules -> rule rules
        rules -> EPSILON
        rule -> rule_header COLON subrule_list SEMICOLON
        rule -> NAME COLON REGEX SEMICOLON
        rule_header -> NAME RETURNS OPEN type CLOSE
        type -> NAME
        type -> NAME OPEN_TRIANGULAR coma_type CLOSE_TRIANGULAR
        coma_type -> type COMA coma_type
        coma_type -> EPSILON
        subrule_list -> subrule OR subrule_list
        subrule_list -> subrule
        subrule -> names CODE
        names -> NAME names
        names -> EPSILON
    */
    val inputCode = """
        val (tokens, rules) = %s.partition { it is lalr.Token }
        Input(${'$'}0, %s, rules.map { it as StarterRule }, tokens.map { it as lalr.Token })""".trimIndent()


    val ruleList = listOf(
        /* input = grammar header rules
           input = grammar rules        */
        StarterRule(Rule("input", listOf("grammar", "header", "rules")), inputCode.format("${'$'}2", "${'$'}1"), "Input"),
        StarterRule(Rule("input", listOf("grammar", "rules")), inputCode.format("${'$'}1", "\"\""), "Input"),
        /* grammar = GRAMMAR NAME ; */
        StarterRule(Rule("grammar", listOf("GRAMMAR", "NAME", "SEMICOLON")), "${'$'}1.text", "String"),
        /* header = HEADER CODE */
        StarterRule(Rule("header", listOf("HEADER", "CODE")),
            "${'$'}1.text.let { it.substring(1, it.lastIndex) }", "String"),
        /* rules = rule rules
           rules = E          */
        StarterRule(Rule("rules", listOf("rule", "rules")), "(${'$'}1 + ${'$'}0).asReversed()", "List<Any>"),
        StarterRule(Rule("rules", listOf(EPSILON)), "emptyList<Any>()", "List<Any>"),
        /* rule = rule_header : subrule_list ;
           rule = NAME : REGEX ;            */
        StarterRule(Rule("rule", listOf("rule_header", "COLON", "subrule_list", "SEMICOLON")),
            "${'$'}2.map { StarterRule(Rule(${'$'}0.first, it.first), it.second, ${'$'}0.second) }", "List<Any>"),
        StarterRule(Rule("rule", listOf("NAME", "COLON", "REGEX", "SEMICOLON")),
            "listOf(lalr.Token(${'$'}0.text, Regex(\"${'$'}{${'$'}2.text.let { it.substring(1, it.lastIndex) }}\")))",
            "List<Any>"),
        /* rule_header = NAME RETURNS [ NAME ] */
        StarterRule(Rule("rule_header", listOf("NAME", "RETURNS", "OPEN_BRACKET", "type", "CLOSE_BRACKET")),
            "${'$'}0.text to ${'$'}3", "Pair<String, String>"),
        /* type -> NAME
           type -> NAME OPEN_TRIANGULAR coma_type CLOSE_TRIANGULAR */
        StarterRule(Rule("type", listOf("NAME")), "${'$'}0.text", "String"),
        StarterRule(Rule("type", listOf("NAME", "OPEN_TRIANGULAR", "coma_type", "CLOSE_TRIANGULAR")),
            "\"${'$'}{${'$'}0.text}<${'$'}${'$'}2>\"", "String"),
        /* coma_type -> type COMA coma_type
           coma_type -> EPSILON             */
        StarterRule(Rule("coma_type", listOf("type", "COMA", "coma_type")), "${'$'}0", "String"),
        StarterRule(Rule("coma_type", listOf(EPSILON)), "\"\"", "String"),
        /* subrule_list = subrule | subrule_list
           subrule_list = subrule                */
        StarterRule(Rule("subrule_list", listOf("subrule", "OR", "subrule_list")),
            "${'$'}2 + ${'$'}0", "List<Pair<List<String>, String>>"),
        StarterRule(Rule("subrule_list", listOf("subrule")),
            "listOf(${'$'}0)", "List<Pair<List<String>, String>>"),
        /* subrule = names CODE */
        StarterRule(Rule("subrule", listOf("names", "CODE")),
            "${'$'}0 to ${'$'}1.text.let { it.substring(1, it.lastIndex) }", "Pair<List<String>, String>"),
        /* names = NAME names
           names = E          */
        StarterRule(Rule("names", listOf("NAME", "names")), "listOf(${'$'}0.text) + ${'$'}1", "List<String>"),
        StarterRule(Rule("names", listOf(EPSILON)), "emptyList<String>()", "List<String>")
    )

    val start = "input"

    val filePrefix = "src/lalr/input/Input"
    val name = "Input"
    val parserHeader =
        """
        package lalr.input

        import lalr.*
        """.trimIndent()

    try {
        writeGenerator(filePrefix, name, tokens, ruleList, start, parserHeader)
    } catch (e: Exception) {
        println("FAILURE: $e")
    }
}
