package lalr

import java.util.Queue
import java.util.LinkedList


open class SyntaxAnalyzerGenerationException(val errorMessage: String): ParserGenerationException(errorMessage)

data class Rule(val name: String, val production: List<String>, val pointer: Int = 0) {
    val pointed
        get() = production[pointer]

    override fun toString() =
        name + " -> " +
        (production.subList(0, pointer)
        + listOf("•")
        + if (pointer == production.size) emptyList<String>() else production.subList(pointer, production.size))
        .joinToString(separator = " ")
}

data class ExtendedRule(val name: String, val production: List<String>, val indices: List<Int>) {
    override fun toString() =
        "${indices.first()} name ${indices.last()} -> " +
        indices.subList(1, indices.size).zip(production) { i, p -> "$i $p" }.joinToString(separator = " ")
}


fun assertValidRules(rules: List<Rule>, tokens: List<Token>) {
    val names = HashSet(rules.map(Rule::name) + tokens.map(Token::name))
    rules.forEach { r ->
        r.production.forEach { prod ->
            if (!names.contains(prod)) {
                throw SyntaxAnalyzerGenerationException("rule \"$prod\" needed by \"${r.name} cannot be found")
            }
        }
    }
}

fun addRule(rules: Map<String, List<Rule>>, tokens: Map<String, List<Token>>, rule: Rule, itemSet: MutableSet<Rule>) {
    if (itemSet.contains(rule)) return

    itemSet.add(rule)
    if (rule.pointer == rule.production.size || !rules.containsKey(rule.pointed)) return
    rules[rule.production[rule.pointer]]!!.forEach { addRule(rules, tokens, it.copy(pointer = 0), itemSet) }
}

fun createItemSets (
    rules: Map<String, List<Rule>>,
    tokens: Map<String, List<Token>>,
    start: Rule
): Pair<List<Set<Rule>>, List<Map<String, Int>>> {

    val itemSetList = ArrayList<Set<Rule>>()
    val translationTable = ArrayList<MutableMap<String, Int>>()
    val itemSetMap = HashMap<Set<Rule>, Int>()

    val q: Queue<Pair<List<Rule>, Int>> = LinkedList<Pair<List<Rule>, Int>>()
    q.add(listOf(start) to -1)

    while (!q.isEmpty()) {
        val (currentStart, from) = q.poll()

        val itemSet = HashSet<Rule>()
        currentStart.forEach { addRule(rules, tokens, it, itemSet) }

        if (!itemSetMap.containsKey(itemSet)) {
            itemSetList.add(itemSet)
            itemSetMap[itemSet] = itemSetList.lastIndex
            translationTable.add(HashMap<String, Int>())

            itemSet
            .filter { it.production.size > it.pointer }
            .groupBy { it.production[it.pointer] }
            .forEach { (_, newSetStart) ->
                q.add(newSetStart.map { it.copy(pointer = it.pointer + 1) } to itemSetList.lastIndex)
            }
        }

        val element = currentStart.first()
        if (from != -1) {
            translationTable[from][element.production[element.pointer - 1]] = itemSetMap[itemSet]!!
        }
    }

    /* DEBUG */
    itemSetList.forEachIndexed { index, it ->
        println(index)
        it.forEach { that -> println("    $that") }
    }

    val maxSpace = minOf(4, rules.values.flatten().map { it.name.length }.max()!! + 1)
    println("-".repeat(30))
    print("    ")
    for (i in translationTable.indices) {
        val str = i.toString()
        print("${" ".repeat(maxSpace - str.length)}$str")
    }
    println()

    translationTable.forEachIndexed { i, m ->
        val iStr = i.toString()
        print("${" ".repeat(4 - iStr.length)}$iStr")
        val row = MutableList<String?>(itemSetList.size) { null }

        m.forEach { (from, to) -> row[to] = from }
        row.forEach { str ->
            print(str?.let {"${" ".repeat(maxSpace - it.length)}$it" } ?: " ".repeat(maxSpace))
        }
        println()
    }
    println()

    return itemSetList to translationTable
}

fun extendGrammar(itemSets: List<Set<Rule>>, translationTable: List<Map<String, Int>>): List<ExtendedRule> =
    itemSets
        .mapIndexed { index, itemSet ->
            itemSet.mapNotNull mapping@ {
                if (it.pointer != 0) return@mapping null

                val indices = ArrayList<Int>()
                var currentIndex = index
                indices.add(currentIndex)

                for (i in it.production.indices) {
                    currentIndex = translationTable[currentIndex][it.production[i]]!!
                    indices.add(currentIndex)
                }
                ExtendedRule(it.name, it.production, indices)
            }
        }
        .flatten()
        /* DEBUG */
        .also { it.forEach(::println) }

fun createFirst(ruleList: List<Rule>): List<Set<String>> {
    return emptyList<Set<String>>()
}

fun generateSyntaxAnalyzer(ruleList: List<Rule>, tokenList: List<Token>, start: String): String {
    println(">> Generating Syntax Analyzer")
    assertValidRules(ruleList, tokenList)

    val rules = ruleList.groupBy(Rule::name)
    val tokens = tokenList.groupBy(Token::name)
    val startRule = Rule("!Start", listOf(start))

    println(">>> Creating Item Sets and Translation Table")
    val (itemSets, translationTable) = createItemSets(rules, tokens, startRule)
    println(">>> Creating Extended Grammar")
    val extendedGrammar = extendGrammar(itemSets, translationTable)


    /*
        TODO
        first
        follow
        goto and actions
    */
    return ""
}


/* DEBUG */

fun main() {
    val ruleList = listOf(
        Rule("N", listOf("V", "=", "E")),
        Rule("N", listOf("E")),
        Rule("E", listOf("V")),
        Rule("V", listOf("x")),
        Rule("V", listOf("*", "E"))
    )

    val tokenList = listOf(
        Token("=", "="),
        Token("*", "*"),
        Token("x", "x")
    )

    val start = "N"
    generateSyntaxAnalyzer(ruleList, tokenList, start)
}
