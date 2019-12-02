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
        "${indices.first()} $name ${indices.last()} -> " +
        indices.subList(1, indices.size).zip(production) { i, p -> "$i $p" }.joinToString(separator = " ")
}


fun assertValidRules(rules: List<Rule>, tokens: Set<String>) {
    val names = HashSet(rules.map(Rule::name) + tokens)
    rules.forEach { r ->
        r.production.forEach { prod ->
            if (!names.contains(prod)) {
                throw SyntaxAnalyzerGenerationException("rule \"$prod\" needed by \"${r.name} cannot be found")
            }
        }
    }
}

fun addRule(rules: Map<String, List<Rule>>, tokens: Set<String>, rule: Rule, itemSet: MutableSet<Rule>) {
    if (itemSet.contains(rule)) return

    itemSet.add(rule)
    if (rule.pointer == rule.production.size || !rules.containsKey(rule.pointed)) return
    rules[rule.production[rule.pointer]]!!.forEach { addRule(rules, tokens, it.copy(pointer = 0), itemSet) }
}

fun createItemSets (
    rules: Map<String, List<Rule>>,
    tokens: Set<String>,
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

    // verbose
    itemSetList.forEachIndexed { index, it ->
        logln(index)
        it.forEach { that -> logln("    $that") }
    }

    val maxSpace = maxOf(2, rules.values.flatten().map { it.name.length }.max()!! + 1)
    logln("-".repeat(30))
    log("    ")
    for (i in translationTable.indices) {
        val str = i.toString()
        log("${" ".repeat(maxSpace - str.length)}$str")
    }
    logln()

    translationTable.forEachIndexed { i, m ->
        val iStr = i.toString()
        log("${" ".repeat(4 - iStr.length)}$iStr")
        val row = MutableList<String?>(itemSetList.size) { null }

        m.forEach { (from, to) -> row[to] = from }
        row.forEach { str ->
            log(str?.let {"${" ".repeat(maxSpace - it.length)}$it" } ?: " ".repeat(maxSpace))
        }
        logln()
    }
    logln()

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
        .also { it.forEach { logln(it)} } // verbose

fun addFirst(ruleList: List<ExtendedRule>, indicesMap: Map<String, List<Int>>, name: String, first: List<MutableSet<String>>) { // TODO fix loops
    if (first[indicesMap[name]!!.first()].isNotEmpty()) return

    val rule = ruleList[indicesMap[name]!!.first()]
    for (part in rule.production) {
        val partSet = first[indicesMap[part]!!.first()]
        if (partSet.isEmpty()) {
            addFirst(ruleList, indicesMap, part, first)
        }

        indicesMap[name]!!.forEach { first[it].addAll(partSet) }
        if (!partSet.contains(EPSILON)) break
    }
}

fun createFirst(ruleList: List<ExtendedRule>, terminals: Set<String>): List<Set<String>> {
    val first = MutableList<MutableSet<String>>(ruleList.size) { HashSet<String>() }
    val indicesMap = ruleList.indices.groupBy { ruleList[it].name }

    val (terminalLeading, nonTerminalLeading) = ruleList.partition { terminals.contains(it.production.first()) }
    terminalLeading.forEach { rule -> indicesMap[rule.name]!!.forEach { first[it].add(rule.production.first()) } }
    nonTerminalLeading.forEach { addFirst(ruleList, indicesMap, it.name, first) }

    // verbose
    indicesMap.entries.map { (s, i) -> s to i.first() }.forEach { (s, i) ->
        logln("FIRST($s) = {${first[i].joinToString()}}")
    }
    return first
}

fun createFollow(ruleList: List<ExtendedRule>, terminals: Set<String>): Map<Pair<String, Int>, Set<String>> {
    val follow = HashMap<Pair<String, Int>, MutableSet<String>>()
    follow.getOrPut(START to 0) { HashSet<String>() }.add(EOF)
    /* val delayedSubstitution = MutableList<MutableSet<String>>(ruleList.size) { HashSet<String>() } */

    ruleList.forEach { rule ->
        rule.production.subList(0, rule.production.lastIndex).forEachIndexed traversing@ { index, prod ->
            if (terminals.contains(prod)) return@traversing

            if (terminals.contains(rule.production[index + 1])) {
                follow.getOrPut(prod to rule.indices[index]) { HashSet<String>() }.add(rule.production[index + 1])
            }


        }
        rule.production.last().let {
            if (!terminals.contains(it)) {
                /* delayedSubstitution */
                TODO()
            }
        }
    }

    return follow
}

fun generateSyntaxAnalyzer(ruleList: List<Rule>, tokens: Set<String>, start: String): String {
    println(">> Generating Syntax Analyzer")
    ruleList.forEach { logln(it) } // verbose

    assertValidRules(ruleList, tokens)

    val rules = ruleList.groupBy(Rule::name)
    val startRule = Rule(START, listOf(start))

    println(">>> Creating Item Sets and Translation Table")
    val (itemSets, translationTable) = createItemSets(rules, tokens, startRule)

    println(">>> Creating Extended Grammar")
    val extendedGrammar = extendGrammar(itemSets, translationTable)

    println(">>> Creating First")
    val first = createFirst(extendedGrammar, tokens)

    println(">>> Creating Follow")
    val follow = createFollow(extendedGrammar, tokens)

    /*
        TODO
        first: rewrite in extended style
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
    generateSyntaxAnalyzer(ruleList, tokenList.map(Token::name).toSet(), start)
}
