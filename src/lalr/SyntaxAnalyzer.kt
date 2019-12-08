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
        "${indices.first()} $name ${indices.last()} -> ${indices.first()} " +
        indices.subList(1, indices.size - 1).zip(production) { i, p -> "$p $i" }.joinToString(separator = " ")
}

abstract class Action(index: Int)

data class Shift(val index: Int): Action(index)
data class Reduce(val index: Int): Action(index)
data class Goto(val index: Int): Action(index)


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

    logln("-".repeat(30))
    logMaps(translationTable)

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

                indices.add(translationTable[index][it.name] ?: -1)
                ExtendedRule(it.name, it.production, indices)
            }
        }
        .flatten()
        .also { it.forEachIndexed { index, er -> logln("$index. $er")} } // verbose

fun addFirst(ruleList: List<ExtendedRule>, indicesMap: Map<String, List<Int>>, num: Int, first: List<MutableSet<String>>) { // TODO fix loops
    if (first[num].isNotEmpty()) return

    val rule = ruleList[num]

    for (i in rule.production.indices) {
        val part = rule.production[i]
        val partList = indicesMap[part]!!
        val partIndex = partList.indexOfFirst {
            ruleList[it].name == part && ruleList[it].indices.first() == rule.indices[i]
        }
        val partSet = first[partList[partIndex]]

        if (partSet.isEmpty()) {
            addFirst(ruleList, indicesMap, partIndex, first)
        }

        first[num].addAll(partSet)
        if (!partSet.contains(EPSILON)) break
    }
}

fun createFirst(ruleList: List<ExtendedRule>, terminals: Set<String>): List<Set<String>> {
    val first = MutableList<MutableSet<String>>(ruleList.size) { HashSet<String>() }
    val indicesMap = ruleList.indices.groupBy { ruleList[it].name }

    val (terminalLeading, nonTerminalLeading) = ruleList.zip(ruleList.indices)
        .partition { (r, _) -> terminals.contains(r.production.first()) }
    terminalLeading.forEach { (rule, _) -> indicesMap[rule.name]!!.forEach { first[it].add(rule.production.first()) } }
    nonTerminalLeading.forEach { (_, i) -> addFirst(ruleList, indicesMap, i, first) }

    // verbose
    indicesMap.entries.forEach { (s, il) ->
        il.forEach { i ->
            val inds = ruleList[i].indices
            logln("FIRST(${inds.first()} $s ${inds.last()}) = {${first[i].joinToString()}}")
        }
    }
    return first
}

fun findExtendedRule(ruleList: List<ExtendedRule>, rule: ExtendedRule, left: Int): List<Int> =
    ruleList.mapIndexedNotNull { index, el ->
        if (el.name == rule.production[left]
            && rule.indices[left] ==  el.indices.first()
            && rule.indices[left + 1] == el.indices.last()) {
            index
        } else {
            null
        }
    }

fun createFollow(
    ruleList: List<ExtendedRule>,
    terminals: Set<String>,
    first: List<Set<String>>
): List<Set<String>> /* Map<Pair<String, Int>, Set<String>> */ { /* TODO DEBUG */

    val follow = HashMap<Pair<String, Int>, MutableSet<String>>()
    follow.getOrPut(START to 0) { HashSet<String>() }.add(EOF)
    val delayedSubstitution = HashMap<Int, MutableList<Int>>()

    ruleList.forEachIndexed { ruleIndex, rule ->
        /* println("$ruleIndex. $rule") */
        rule.production.subList(0, rule.production.lastIndex).forEachIndexed traversing@ { index, prod ->
            if (terminals.contains(prod)) return@traversing

            var subIndex = index + 1

            follow.getOrPut(prod to rule.indices[index]) { HashSet<String>() }.addAll(
                if (terminals.contains(rule.production[subIndex])) {
                    setOf(rule.production[subIndex])
                } else {
                    var currentIndices = findExtendedRule(ruleList, rule, subIndex)
                    /* val firstIndices = currentIndices */

                    val result = HashSet<String>()
                    while (currentIndices.any { first[it].contains(EPSILON) } && subIndex < rule.production.size) {
                        result.addAll(currentIndices.map { first[it] }.flatten())
                        result.remove(EPSILON)
                        subIndex++
                        currentIndices = findExtendedRule(ruleList, rule, subIndex)
                    }

                    if (subIndex < rule.production.size) {
                        delayedSubstitution.getOrPut(ruleIndex) { ArrayList<Int>() }.addAll(currentIndices)
                    } else {
                        result.addAll(currentIndices.map { first[it] }.flatten())
                    }
                    result
                }
            )
        }

        if (!terminals.contains(rule.production.last())) {
            delayedSubstitution.getOrPut(ruleIndex) { ArrayList<Int>() }.addAll(findExtendedRule(ruleList, rule, rule.production.lastIndex))
        }
    }

    /* delayedSubstitution.forEach { (from, to) ->
        println("$from) _FOLLOW(${ruleList[from].indices.first()} ${ruleList[from].name}) -> ${to.joinToString()}")
    } */

    while (delayedSubstitution.isNotEmpty()) { /* TODO fix cycles?? */
        val valuesSet = delayedSubstitution.values.flatten().toSet()
        val doneList = ArrayList<Int>()
        /* println("vs: $valuesSet") */

        val was = delayedSubstitution.size /* DEBUG */

        delayedSubstitution.forEach { from, to ->
            if (!valuesSet.contains(from)) {
                to.forEach {
                    follow.getOrPut(ruleList[it].name to ruleList[it].indices.first()) {
                        HashSet<String>()
                    }
                    .addAll(
                        follow.getOrDefault(
                            ruleList[from].name to ruleList[from].indices.first(),
                            HashSet<String>()
                        )
                    )
                }
                doneList.add(from)
            }
        }

        doneList.forEach { delayedSubstitution.remove(it) }
        /* println("=".repeat(30)) */
        /* delayedSubstitution.forEach { (from, to) ->
            println("_FOLLOW(${ruleList[from].indices.first()} ${ruleList[from].name}) -> ${to.joinToString()}")
        } */

        if (was == delayedSubstitution.size) { /* DEBUG */
            break
        }
    }

    // verbose
    follow.forEach { (s, ss) -> logln("FOLLOW(${s.second} ${s.first}) = { ${ss.joinToString()} }")}

    /* return follow */
    return List<Set<String>>(ruleList.size) { HashSet<String>() }
}

fun createGotos(
    ruleList: List<ExtendedRule>,
    terminals: Set<String>,
    itemSets: List<Set<Rule>>,
    translationTable: List<Map<String, Int>>,
    follow: List<Set<String>>
): List<Map<String, Action>> {

    val transitions = MutableList<MutableMap<String, Action>>(itemSets.size) { HashMap<String, Action>() }

    val finishIndex = itemSets.indexOfFirst { it.find { it.name == START && it.pointer == it.production.size } != null }
    transitions[finishIndex][EOF] = Goto(-1)

    transitions.forEachIndexed { index, it ->
        it.putAll(
            translationTable[index]
                .map { (k, v) -> k to if (terminals.contains(k)) Goto(v) else Shift(v) }
        )
    }

    ruleList
        .indices
        .groupBy {
            val indices = ruleList[it].indices
            indices[indices.size - 2]
        }
        .forEach { newIndex, previous ->
            val ruleIndex = previous.first()

            previous
                .asSequence()
                .map { follow[it] }
                .flatten()
                .toSet()
                .forEach { transitions[newIndex][it] = Reduce(ruleIndex) }
        }

    logMaps(transitions) // verbose
    return transitions
}

fun createOutput(extendedGrammar: List<ExtendedRule>, gotos: List<Map<String, Action>>): String {
    val grammarString = extendedGrammar
        .asSequence()
        .map {
            val productionString = it.production.map { "\"$it\"" }.joinToString()
            val indicesString = it.indices.joinToString()

            "ExtendedRule(${it.name}, listOf($productionString), listOf($indicesString))"
        }
        .joinToString("\n${indent(2)}", "listOf<ExtendedRule>(\n${indent(2)}", "\n${indent()})")

    val gotosString = gotos.map {
        val mapString = it.asSequence().map { (k, v) -> "\"$k\" to $v" }.joinToString()
        "mapOf<String, Action>($mapString)"
    }.joinToString("\n${indent(2)}", "listOf<Map<String, Action>>(\n${indent(2)}", "\n${indent()})")


    return """
    val extendedGrammar = $grammarString
    val gotos = $gotosString
    """.trimIndent()
}

val syntaxAnalyzerTemplate = """
fun parse%s(ins: InputStream) {
    lex = LexicalAnalyzer(ins)
    lex.nextToken()

}

%s
"""

fun generateSyntaxAnalyzer(name: String, ruleList: List<Rule>, tokens: Set<String>, start: String): String {
    println(">> Generating Syntax Analyzer")
    ruleList.forEachIndexed { index, it -> logln("$index. $it") } // verbose

    assertValidRules(ruleList, tokens)
    val rules = ruleList.groupBy(Rule::name)
    val startRule = Rule(START, listOf(start))

    println(">>> Creating Item Sets and Translation Table")
    val (itemSets, translationTable) = createItemSets(rules, tokens, startRule)

    println(">>> Extending Grammar")
    val extendedGrammar = extendGrammar(itemSets, translationTable)

    println(">>> Creating First")
    val first = createFirst(extendedGrammar, tokens)

    println(">>> Creating Follow")
    val follow = createFollow(extendedGrammar, tokens, first)

    println(">>> Creating Actions and Gotos tables")
    val gotos = createGotos(extendedGrammar, tokens, itemSets, translationTable, follow)

    return syntaxAnalyzerTemplate.format(name, createOutput(extendedGrammar, gotos)).also {
        logln("=".repeat(30)) /* TEMP */
        logln(it)
    }
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
    generateSyntaxAnalyzer("Sample", ruleList, tokenList.map(Token::name).toSet(), start)
}
