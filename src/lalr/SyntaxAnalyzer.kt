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

abstract class Action(open val index: Int) {
    abstract fun tableString(): String
}

data class Shift(override val index: Int): Action(index) {
    override fun tableString(): String = "s$index"
}

data class Reduce(override val index: Int): Action(index) {
    override fun tableString(): String = "r$index"
}

data class Goto(override val index: Int): Action(index) {
    override fun tableString(): String = "$index"
}


fun assertValidRules(rules: List<Rule>, tokens: Set<String>, start: String) {
    val names = HashSet(rules.map(Rule::name) + tokens)
    rules.forEach { r ->
        r.production.forEach { prod ->
            if (!names.contains(prod))
                throw SyntaxAnalyzerGenerationException("rule \"$prod\" needed by \"${r.name}\" cannot be found")
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
        if (it.code.isEmpty())
            throw SyntaxAnalyzerGenerationException("empty code section for \"${it.rule.name}\"")
    }
}

fun addFirst(
    ruleList: List<Rule>,
    indicesMap: Map<String, List<Int>>,
    num: Int,
    first: MutableMap<String, MutableSet<String>>,
    terminals: Set<String>,
    visited: MutableSet<Int> = HashSet<Int>()
) {
    val rule = ruleList[num]
    val name = rule.name
    visited.add(num)

    for (i in rule.production.indices) {
        val part = rule.production[i]
        if (terminals.contains(part)) {
            first[name]!!.add(part)
            break
        }

        val partIndex = indicesMap[part]!!
        var partSet = partIndex.asSequence().flatMap { first[ruleList[it].name]!!.asSequence() }.toSet()

        if (partSet.isEmpty()) {
            partIndex.forEach { if (!visited.contains(it)) addFirst(ruleList, indicesMap, it, first, terminals, visited) }
            partSet = partIndex.asSequence().flatMap { first[ruleList[it].name]!!.asSequence() }.toSet()
        }

        first[name]!!.addAll(partSet)
        if (!partSet.contains(EPSILON)) break
    }
    visited.remove(num)
}

fun createFirst(ruleList: List<Rule>, terminals: Set<String>): Map<String, Set<String>> {
    val first = HashMap<String, MutableSet<String>>()
    ruleList.forEach { first[it.name] = HashSet<String>() }
    val indicesMap = ruleList.indices.groupBy { ruleList[it].name }

    val (terminalLeading, nonTerminalLeading) = ruleList.withIndex()
        .partition { (_, r) -> terminals.contains(r.production.first()) }

    terminalLeading.forEach { (_, rule) -> first[rule.name]!!.add(rule.production.first()) }
    nonTerminalLeading.forEach { (i, _) -> addFirst(ruleList, indicesMap, i, first, terminals) }

    // verbose
    val maxSpace = 1 + (indicesMap.keys.asSequence().map { it.length }.max() ?: 0)
    first.entries.forEach { (s, il) ->
        logln("FIRST($s)${" ".repeat(maxSpace - s.length)}│ {${il.joinToString()}}")
    }
    return first
}

fun addRule(rules: Map<String, List<Rule>>, tokens: Set<String>, rule: Rule, itemSet: MutableSet<Rule>) {
    if (itemSet.contains(rule)) return

    itemSet.add(rule)
    if (rule.pointer >= rule.production.size) return
    rules[rule.pointed]?.forEach { addRule(rules, tokens, it.copy(pointer = 0), itemSet) }
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
                .filter { it.production.size > it.pointer && it.pointed != EPSILON }
                .groupBy { it.pointed }
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
        logln("$index ${"─".repeat(99 - index.toString().length)}")
        it.forEach { that -> logln("    $that") }
    }
    logln("─".repeat(100))
    logMaps(translationTable) { it.toString() }

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

                if (it.production == listOf(EPSILON)) {
                    indices.add(currentIndex)
                    return@mapping ExtendedRule(it.name, it.production, indices)
                }

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

fun findExtendedRule(ruleList: List<ExtendedRule>, rule: ExtendedRule, left: Int): List<Int> =
    ruleList.mapIndexedNotNull { index, el ->
        if (el.name == rule.production[left]
            && rule.indices[left] ==  el.indices.first()
            && rule.indices[left + 1] == el.indices.last()
        ) {
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
        if (result != null) return result
    }
    return null
}

fun createFollow(
    ruleList: List<ExtendedRule>,
    terminals: Set<String>,
    first: Map<String, Set<String>>
): List<Set<String>> {

    val followMap = HashMap<Pair<String, Int>, MutableSet<String>>()
    followMap.getOrPut(START to 0) { HashSet<String>() }.add(EOF)
    var delayedSubstitution = HashMap<Int, MutableList<Int>>()

    ruleList.forEachIndexed { ruleIndex, rule ->
        rule.production
            .subList(0, rule.production.lastIndex)
            .forEachIndexed traversing@ { index, prod ->

                if (terminals.contains(prod)) return@traversing
                var subIndex = index + 1

                followMap
                    .getOrPut(prod to rule.indices[index]) { HashSet<String>() }
                    .addAll(
                        if (terminals.contains(rule.production[subIndex])) {
                            setOf(rule.production[subIndex])
                        } else {
                            var currentIndices = findExtendedRule(ruleList, rule, subIndex)

                            val result = HashSet<String>()
                            while (currentIndices.any { first[ruleList[it].name]!!.contains(EPSILON) }
                                    && subIndex < rule.production.size) {
                                currentIndices = findExtendedRule(ruleList, rule, subIndex)
                                result.addAll(currentIndices.flatMap { first[ruleList[it].name]!! })
                                result.remove(EPSILON)
                                subIndex++
                            }

                            if (subIndex == rule.production.size) {
                                delayedSubstitution
                                    .getOrPut(ruleIndex) { ArrayList<Int>() }
                                    .addAll(findExtendedRule(ruleList, rule, index))
                            } else {
                                result.addAll(currentIndices.map { first[ruleList[it].name]!! }.flatten())
                            }
                            result
                        }
                    )
            }

        if (!terminals.contains(rule.production.last())) {
            delayedSubstitution
                .getOrPut(ruleIndex) { ArrayList<Int>() }
                .addAll(findExtendedRule(ruleList, rule, rule.production.lastIndex))
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

        if (was == delayedSubstitution.size) {
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
    val maxSpace = 1 +
        (followMap.keys.asSequence().map { it.first.length + it.second.toString().length }.max() ?: 0)
    followMap.forEach { (s, ss) ->
        val spacesLeft = maxSpace - s.first.length - s.second.toString().length
        logln("FOLLOW(${s.second} ${s.first})${" ".repeat(spacesLeft)}│ { ${ss.joinToString()} }")
    }

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
        .asSequence()
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
                .forEach {
                    val errorMessage = "%s/reduce conflict found, apparently not LALR grammar"
                    when (transitions[newIndex][it]) {
                        is Reduce -> throw SyntaxAnalyzerGenerationException(errorMessage.format("reduce"))
                        is Shift  -> throw SyntaxAnalyzerGenerationException(errorMessage.format("shift"))
                    }

                    transitions[newIndex][it] = Reduce(ruleIndex)
                }
        }

    transitions[finishIndex][EOF] = Goto(-1)

    logMaps(transitions) { it.tableString() } // verbose
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

    val gotosString = gotos
        .map {
            val mapString = it.asSequence().map { (k, v) -> "\"$k\" to $v" }.joinToString()
            "mapOf<String, Action>($mapString)"
        }
        .joinToString(",\n|${indent(2)}", "listOf<Map<String, Action>>(\n|${indent(2)}", "\n|${indent()})")



    return """
    |${indent()}val extendedGrammar = $grammarString
    |${indent()}val gotos = $gotosString
    """.trimMargin()
}

fun createRulesArguments(rule: Rule, types: Map<String, String>, used: Set<Int>): String {
    if (rule.production == listOf(EPSILON)) return ""
    return rule
        .production
        .asReversed()
        .asSequence()
        .mapIndexed { index, name ->
            val inputIndex = rule.production.lastIndex - index
            val postFix = "safePop<${types[name] ?: "Token"}>()"
            if (used.contains(inputIndex)) "val arg${rule.production.lastIndex - index} = " + postFix else postFix
        }
        .joinToString("\n|${indent(2)}")
}

fun createRulesCode(rules: List<StarterRule>): String {
    val types = rules.asSequence().map { it.rule.name to it.returnType }.toMap()
    val argRegex = Regex("\\${'$'}\\d+")

    return rules
        .asSequence()
        .map {
            val lines = it.code
                .replace(argRegex) { "arg${it.value.substringAfterLast('$')}" }
                .lines()
                .map(String::trim)
            val concatenated = lines
                .subList(0, lines.size - 1)
                .joinToString("\n${indent(2)}", "|${indent(2)}", "\n")
                .ifEmpty { "" } +
                "|${indent(2)}output.push(${lines.last()})"
            val usedArgs = argRegex.findAll(it.code).map { it.value.substring(1, it.value.length).toInt() }.toSet()

            """
            |${indent()}private fun `${it.rule.extendedName}`() {
            |${indent(2)}${createRulesArguments(it.rule, types, usedArgs)}
            $concatenated
            |${indent()}}
            """.trimMargin()
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
    logln("tokens: ${tokens.joinToString()}")

    val updatedTokens = tokens + EPSILON + EOF

    assertValidRules(ruleList, updatedTokens, start)
    assertValidCodes(starterRules)
    val rules = ruleList.groupBy(Rule::name)
    val startRule = Rule(START, listOf(start))

    println(">>> Creating First")
    val first = createFirst(ruleList, updatedTokens)

    println(">>> Creating Item Sets and Translation Table")
    val (itemSets, translationTable) = createItemSets(rules, updatedTokens, startRule)

    println(">>> Extending Grammar")
    val extendedGrammar = extendGrammar(itemSets, translationTable)

    println(">>> Creating Follow")
    val follow = createFollow(extendedGrammar, updatedTokens, first)

    println(">>> Creating Actions and Gotos tables")
    val gotos = createGotos(extendedGrammar, updatedTokens, itemSets, translationTable, follow)


    val returnType = starterRules.find { it.rule.name == start }!!.returnType
    val rulesCode = createRulesCode(starterRules + StarterRule(startRule, "${'$'}0", returnType))
    val header = parserHeader.lines().asSequence().map(String::trim).joinToString("\n")

    return syntaxAnalyzerTemplate.format(name, createOutput(extendedGrammar, gotos), rulesCode, returnType, header)
}

val syntaxAnalyzerTemplate =
    """
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

    const val EPSILON = "!EPSILON"


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

                        val prod = extendedGrammar[currentTransition.index].production
                        if (prod != listOf(EPSILON)) {
                            prod.indices.forEach { st.pop() }
                        }

                        st.push(gotos[st.peek()][extendedGrammar[currentTransition.index].name]?.index ?: noTransition())
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
            throw SyntaxException("No rule for \"${'$'}{ lex.curToken.name }\" at ${'$'}{ lex.curLine }:${'$'}{ lex.curIndex }", lex.curPos)
        }

        inline private fun <reified T> safePop(): T {
            if (output.empty()) {
                throw SyntaxException("Expected more tokens", lex.curPos)
            }
            if (output.peek() !is T) throw SyntaxException("Got wrong type for \"${'$'}{ output.peek() }\"", lex.curPos)
            return output.pop() as T
        }

    %3${'$'}s
    }
    """.trimIndent()
