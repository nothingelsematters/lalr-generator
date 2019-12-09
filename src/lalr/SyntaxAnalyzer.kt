package lalr

import java.util.Queue
import java.util.Stack
import java.util.LinkedList


open class SyntaxAnalyzerGenerationException(val errorMessage: String): ParserGenerationException(errorMessage)

data class StarterRule(val rule: Rule, val code: String, val returnType: String)

data class Rule(val name: String, val production: List<String>, val pointer: Int = 0) {
    val pointed
        get() = production[pointer]
    val extendedName
        get() = "${name} = ${production.joinToString(" ")}"

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

abstract class Action(open val index: Int)

data class Shift(override val index: Int): Action(index)
data class Reduce(override val index: Int): Action(index)
data class Goto(override val index: Int): Action(index)


fun assertValidRules(rules: List<Rule>, tokens: Set<String>, start: String) {
    val names = HashSet(rules.map(Rule::name) + tokens)
    rules.forEach { r ->
        r.production.forEach { prod ->
            if (!names.contains(prod)) {
                throw SyntaxAnalyzerGenerationException("rule \"$prod\" needed by \"${r.name} cannot be found")
            }
        }
    }
    if (rules.find { it.name == start } == null)
        throw SyntaxAnalyzerGenerationException("there is no start (\"$start\") rule")
}

fun assertValidCodes(rules: List<StarterRule>) {
    val types = HashMap<String, String>()
    rules.forEach {
        val retType = it.returnType
        if (types.getOrPut(it.rule.name) { retType } != retType)
            throw SyntaxAnalyzerGenerationException("different return types for rule \"${it.rule.name}\"")
    }
    rules.forEach {
        if (it.code.isEmpty()) throw SyntaxAnalyzerGenerationException("empty code section for \"${it.rule.name}\"")
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

fun cycleDfs(graph: Map<Int, List<Int>>, current: Int, was: Set<Int> = HashSet<Int>()): Pair<Int, List<Int>>? {
    if (was.contains(current)) {
        return current to listOf<Int>(current)
    }

    graph[current]?.forEach {
        val result = cycleDfs(graph, it, was + current)
        if (result != null) {
            return result.first to
                if (was.contains(result.first) || result.first == current) {
                    result.second + current
                } else {
                    result.second
                }
        }
    }
    return null
}

fun findCycle(graph: Map<Int, List<Int>>): List<Int>? {
    graph.keys.forEach {
        val result = cycleDfs(graph, it)?.second
        if (result != null) {
            return result
        }
    }
    return null
}

fun createFollow(
    ruleList: List<ExtendedRule>,
    terminals: Set<String>,
    first: List<Set<String>>
): List<Set<String>> { /* DEBUG */

    val followMap = HashMap<Pair<String, Int>, MutableSet<String>>()
    followMap.getOrPut(START to 0) { HashSet<String>() }.add(EOF)
    val delayedSubstitution = HashMap<Int, MutableList<Int>>()

    ruleList.forEachIndexed { ruleIndex, rule ->
        rule.production.subList(0, rule.production.lastIndex).forEachIndexed traversing@ { index, prod ->
            if (terminals.contains(prod)) return@traversing

            var subIndex = index + 1

            followMap.getOrPut(prod to rule.indices[index]) { HashSet<String>() }.addAll(
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

    val cycles = mutableListOf<List<Int>>()
    var probableCycle = findCycle(delayedSubstitution)
    while (probableCycle != null) {
        cycles.add(probableCycle)

        for (i in probableCycle.size - 1 downTo 1) {
            val parent = probableCycle[i]

            if (delayedSubstitution[parent]!!.size == 1) {
                delayedSubstitution.remove(parent)
            } else {
                delayedSubstitution[parent] = (delayedSubstitution[parent]!! - probableCycle[i - 1]).toMutableList()
            }
        }
        probableCycle = findCycle(delayedSubstitution)
    }

    while (delayedSubstitution.isNotEmpty()) {
        val valuesSet = delayedSubstitution.values.flatten().toSet()
        val doneList = ArrayList<Int>()

        val was = delayedSubstitution.size

        delayedSubstitution.forEach { from, to ->
            if (!valuesSet.contains(from)) {
                to.forEach {
                    followMap.getOrPut(ruleList[it].name to ruleList[it].indices.first()) {
                        HashSet<String>()
                    }
                    .addAll(
                        followMap.getOrDefault(
                            ruleList[from].name to ruleList[from].indices.first(),
                            HashSet<String>()
                        )
                    )
                }
                doneList.add(from)
            }
        }

        doneList.forEach { delayedSubstitution.remove(it) }

        if (was == delayedSubstitution.size) { /* DEBUG */
            break
        }
    }
    val follow = MutableList<MutableSet<String>>(ruleList.size) {
        val rule = ruleList[it]
        followMap[rule.name to rule.indices.first()] ?: HashSet<String>()
    }

    cycles.indices.forEach {
        cycles.forEach {
            val newFollow = it.asSequence().flatMap { follow[it].asSequence() }.toSet().toMutableSet()
            it.forEach { follow[it] = newFollow }
        }
    }

    // verbose
    followMap.forEach { (s, ss) -> logln("FOLLOW(${s.second} ${s.first}) = { ${ss.joinToString()} }")}

    return follow
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

    transitions.forEachIndexed { index, it ->
        it.putAll(
            translationTable[index]
                .map { (k, v) -> k to if (terminals.contains(k)) Shift(v) else Goto(v) }
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
    transitions[finishIndex][EOF] = Goto(-1)

    logMaps(transitions) // verbose
    return transitions
}

fun createOutput(extendedGrammar: List<ExtendedRule>, gotos: List<Map<String, Action>>): String {
    val grammarString = extendedGrammar
        .asSequence()
        .map {
            val productionString = it.production.map { "\"$it\"" }.joinToString()
            val indicesString = it.indices.joinToString()

            "ExtendedRule(\"${it.name}\", listOf($productionString), listOf($indicesString))"
        }
        .joinToString(",\n|${indent(2)}", "listOf<ExtendedRule>(\n|${indent(2)}", "\n|${indent()})")

    val gotosString = gotos.map {
        val mapString = it.asSequence().map { (k, v) -> "\"$k\" to $v" }.joinToString()
        "mapOf<String, Action>($mapString)"
    }.joinToString(",\n|${indent(2)}", "listOf<Map<String, Action>>(\n|${indent(2)}", "\n|${indent()})")


    return """
    |${indent()}val extendedGrammar = $grammarString
    |${indent()}val gotos = $gotosString
    """.trimMargin("|")
}

fun createRulesArguments(rule: Rule, types: Map<String, String>) =
    rule
        .production
        .asReversed()
        .asSequence()
        .mapIndexed { index, name -> "val arg${rule.production.lastIndex - index} = safePop<${types[name] ?: "Token"}>()" }
        .joinToString("\n|${indent(2)}")

fun createRulesCode(rules: List<StarterRule>): String {
    val types = rules.asSequence().map { it.rule.name to it.returnType }.toMap()

    return rules
        .asSequence()
        .map {
            val newCode = it.code.replace(Regex("\\${'$'}\\d+")) { "arg${it.value.substringAfterLast('$')}" }
            val lines = newCode.lines()
            val first = lines.subList(0, lines.size - 1).joinToString("\n${indent(2)}")
            val second = lines.last()

            """
            |${indent()}private fun `${it.rule.extendedName}`() {
            |${indent(2)}${createRulesArguments(it.rule, types)}
            |${indent(2)}$first
            |${indent(2)}output.push($second)
            |${indent()}}
            """.trimMargin("|")
        }
        .joinToString("\n\n")
}

fun generateSyntaxAnalyzer(
    name: String,
    starterRules: List<StarterRule>,
    tokens: Set<String>,
    start: String,
    parserHeader: String
): String {

    val ruleList = starterRules.map { it.rule }
    println(">> Generating Syntax Analyzer")
    ruleList.forEachIndexed { index, it -> logln("$index. $it") } // verbose

    assertValidRules(ruleList, tokens, start)
    assertValidCodes(starterRules)
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


    val returnType = starterRules.find { it.rule.name == start }!!.returnType
    val rulesCode = createRulesCode(starterRules + StarterRule(startRule, "${'$'}0", returnType))

    return syntaxAnalyzerTemplate.format(name, createOutput(extendedGrammar, gotos), rulesCode, returnType, parserHeader)
}

val syntaxAnalyzerTemplate = """
%5${'$'}s
import java.io.InputStream
import java.util.Stack
import java.text.ParseException


public class SyntaxException(str: String, pos: Int): ParseException(str, pos)

data class ExtendedRule(val name: String, val production: List<String>, val indices: List<Int>) {
    val extendedName
        get() = "${'$'}{name} = ${'$'}{production.joinToString(" ")}"

    override fun toString() =
        "${'$'}{indices.first()} ${'$'}name ${'$'}{indices.last()} -> ${'$'}{indices.first()} " +
        indices.subList(1, indices.size - 1).zip(production) { i, p -> "${'$'}p ${'$'}i" }.joinToString(separator = " ")
}

abstract class Action(open val index: Int)

data class Shift(override val index: Int): Action(index)
data class Reduce(override val index: Int): Action(index)
data class Goto(override val index: Int): Action(index)

public class %1${'$'}sParser {
    private lateinit var output: Stack<Any>
    private lateinit var lex: %1${'$'}sLexer

%2${'$'}s

    public fun parse(ins: InputStream): %4${'$'}s {
        lex = %1${'$'}sLexer(ins)
        val st = Stack<Int>()
        output = Stack<Any>()
        st.push(0)

        lex.nextToken()
        var currentTransition: Action = gotos[st.peek()][lex.curToken.name] ?: noTransition()


        while (currentTransition.index != -1) {
            when (currentTransition) {
                is Shift -> {
                    st.push(currentTransition.index)
                    output.push(lex.curToken)
                    lex.nextToken()
                }

                is Reduce -> {
                    %1${'$'}sParser::class.java.getDeclaredMethod(extendedGrammar[currentTransition.index].extendedName).invoke(this)
                    extendedGrammar[currentTransition.index].production.indices.forEach { st.pop() }
                    st.push(gotos[st.peek()][extendedGrammar[currentTransition.index].name]?.index ?: noTransition()
                }

                else -> noTransition()
            }

            currentTransition = gotos[st.peek()][lex.curToken.name] ?: noTransition()
        }

        if (output.size != 1) {
            throw SyntaxException("There are few more tokens left, starting rule didn't reduce", lex.curPos)
        }
        return safePop<%4${'$'}s>()
    }

    private fun noTransition(): Nothing {
        throw SyntaxException("No rule for \"${'$'}{ lex.curToken.name }\" at", lex.curPos)
    }

    inline private fun <reified T> safePop(): T {
        if (output.empty()) {
            throw SyntaxException("Expected more tokens", lex.curPos)
        }
        if (output.peek() !is T) throw SyntaxException("Got wrong type for \"${'$'}{ output.peek() }\", lex.curPos")
        return output.pop() as T
    }

%3${'$'}s
}
"""
