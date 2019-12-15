package cFunctionHeader

import java.text.ParseException
import java.io.FileOutputStream
import java.io.File
import java.util.Random
import java.util.concurrent.TimeUnit

import kotlin.math.abs

import org.junit.Test
import org.junit.Assume


class IncorrectTestException(val test: String) : Exception(test)


class ParserTest {
    private val generator = TreeGenerator()

    private fun parse(str: String): SyntaxTree = CFunctionHeaderParser().parse(str.byteInputStream())
    private fun stringTest(str: String) {
        parse(str)
    }

    @Test fun `simpliest test`() = stringTest("int f();")

    @Test(expected = LexicalException::class)
    fun `weird symbol test`() = stringTest("int ?? f();")

    @Test(expected = LexicalException::class)
    fun `wrong name format test`() = stringTest("int ?_f();")

    @Test(expected = LexicalException::class)
    fun `numeric beginning name test`() = stringTest("int 1_f();")

    @Test(expected = SyntaxException::class)
    fun `no semicolon test`() = stringTest("int f()")

    @Test(expected = SyntaxException::class)
    fun `no return test`() = stringTest("f();")

    @Test fun `megapointer test`() = stringTest("const signed short int* const **** pointers(struct name* (*f)(long****));")

    @Test fun `system types test`() = stringTest("int f(short, short int, unsigned short int, signed short, long, long long, double, float);")

    @Test fun `structure argument test`() = stringTest("void f_(struct NAME);")

    @Test fun `function specifiers test`() = stringTest("static inline const unsigned long long int foo();")

    @Test fun `middle difficulty test`() =
        stringTest("inline static void A_123(const unsigned long long int * const * b, struct n4ME_ * const * (*func)(signed short* arg));")

    @Test fun `full coverage random grammar test`() = repeat (100) {
        val test = generator.getRandom()
        val file = createTempFile(suffix = ".c")
        with (FileOutputStream(file)) {
            write(test.toByteArray())
        }

        val path = file.absolutePath
        val outputPath = path + ".o"

        val process = Runtime.getRuntime().exec("gcc -o $outputPath -c $path")
        val exited = process.waitFor(6L, TimeUnit.SECONDS)
        val valid = if (exited) process.exitValue() == 0 else null
        if (exited && valid == false) throw IncorrectTestException(test)

        file.delete()
        File(outputPath).delete()


        try {
            stringTest(test)

        } catch (e: Exception) {
            println(test)

            if (!exited) {
                println("WARNING: gcc skipped checking, this test may fail with ${e.message}: $test")
            } else {
                throw e
            }
        }
    }
}

typealias StringFunc = () -> String
typealias StringFuncList = List<StringFunc>

public enum class TreeToken {
    CHAR, SHORT, INT, LONG,
    FLOAT, DOUBLE,
    BOOL,
    SIGNED, UNSIGNED,
    AMPERSAND,
    INLINE, STATIC,
    VOID,
    CONST,
    STRUCT,
    POINTER,          // *
    COMA,             // ,
    LEFTPARENTHESIS,  // (
    RIGHTPARENTHESIS, // )
    SEMICOLON,        // ;
    END,              // $ (fake)
    NAME              // [\w_][\w\d_]*
}

val tokenMap = mapOf(
    TreeToken.POINTER to "*",
    TreeToken.COMA to ",",
    TreeToken.SEMICOLON to ";",
    TreeToken.LEFTPARENTHESIS to "(",
    TreeToken.RIGHTPARENTHESIS to ")",
    TreeToken.AMPERSAND to "&",
    TreeToken.CHAR to "char",
    TreeToken.SHORT to "short",
    TreeToken.INT to "int",
    TreeToken.LONG to "long",
    TreeToken.SIGNED to "signed",
    TreeToken.UNSIGNED to "unsigned",
    TreeToken.INLINE to "inline",
    TreeToken.STATIC to "static",
    TreeToken.VOID to "void",
    TreeToken.CONST to "const",
    TreeToken.STRUCT to "struct",
    TreeToken.END to "end of input",
    TreeToken.NAME to "name",
    TreeToken.FLOAT to "float",
    TreeToken.DOUBLE to "double",
    TreeToken.BOOL to "bool"
)


class TreeGenerator {
    public fun getRandom(): String = function()
    private val r = Random()

    private var argumentsDepth = 0

    private fun epsilon(): String = ""

    private fun concatenate(vararg strings: String): String = concatenate(strings.asList())
    private fun concatenate(strings: List<String>): String = strings.joinToString(separator = " ")

    private fun tokenFork(token: TreeToken, ifPart: StringFuncList, elsePart: StringFuncList): String =
        listOf(listOf({-> processTreeToken(token)}) + ifPart, elsePart).random().map { it() }.joinToString(separator = " ")

    private fun eitherwayTreeTokenFork(token: TreeToken, part: StringFuncList): String = tokenFork(token, part, part)

    private fun processTreeToken(token: TreeToken): String = if (token == TreeToken.NAME) name() else tokenMap[token]!!

    private fun function(): String = concatenate(specifiers(), returnType(), nameAndArgList(), ";")

    private fun specifiers(): String = listOf(tokenMap[TreeToken.STATIC]!!, tokenMap[TreeToken.INLINE]!!, epsilon()).random()

    private fun specifier(): String = listOf(TreeToken.STATIC, TreeToken.INLINE).map { tokenMap[it]!! }.random()

    private fun functionDeclaration(): String = concatenate(returnType(), name(), argsList())

    private fun returnType(): String = tokenFork(TreeToken.VOID, listOf(::epsilon), listOf(::argType))

    private fun argType(): String = eitherwayTreeTokenFork(TreeToken.CONST, listOf(::typeNameModifiers))

    private fun typeNameModifiers(): String = concatenate(typeName(), modifiers())

    private fun typeName(): String = tokenFork(TreeToken.STRUCT, listOf(::name), listOf(::systemTypes))

    private fun systemTypes(): String = listOf(
        concatenate(numericSpecifiers(), systemTypesPrime()),
        processTreeToken(TreeToken.DOUBLE),
        processTreeToken(TreeToken.FLOAT)
    ).random()

    private fun systemTypesPrime(): String =
        listOf(processTreeToken(TreeToken.CHAR), processTreeToken(TreeToken.INT),
            concatenate(processTreeToken(TreeToken.SHORT), int()),
            concatenate(processTreeToken(TreeToken.LONG), long())).random()

    private fun long(): String = "long"

    private fun numericSpecifiers(): String = listOf(processTreeToken(TreeToken.UNSIGNED), processTreeToken(TreeToken.SIGNED), epsilon()).random()

    private fun name(): String = "n4m3${abs(r.nextInt())}"

    private fun processPointer(): String = processTreeToken(TreeToken.POINTER)
    private fun processConst(): String = processTreeToken(TreeToken.CONST)

    private fun modifiers(): String =
        concatenate(listOf(listOf(::processPointer, ::modifiers), listOf(::processConst, ::modifiers), listOf(::epsilon)).random().map { it() })

    private fun nameAndArgList(): String = concatenate(name(), argsList())

    private fun nameAndArgs(): String =
        listOf(concatenate(processTreeToken(TreeToken.LEFTPARENTHESIS), processTreeToken(TreeToken.POINTER), name(),
            processTreeToken(TreeToken.RIGHTPARENTHESIS), argsList()), concatenate(name(), argsList())).random()

    private fun int(): String = "int"

    private fun argument(): String =
        tokenFork(TreeToken.VOID, listOf(::functionArgumentNameAndArgsList), listOf(::argType, ::argRight))

    private fun argRight(): String = listOf(functionArgumentNameAndArgsList(), argumentName()).random()

    private fun functionArgumentNameAndArgsList(): String =
        concatenate(processTreeToken(TreeToken.LEFTPARENTHESIS), processTreeToken(TreeToken.POINTER),
            name(), processTreeToken(TreeToken.RIGHTPARENTHESIS), argsList())

    private fun argumentName(): String =
        tokenFork(TreeToken.NAME, emptyList<StringFunc>(), listOf(::epsilon))

    private fun notEmptyArgs(): String = concatenate(argument(), restArgs())

    private fun restArgs(): String =
        tokenFork(TreeToken.COMA, listOf(::notEmptyArgs), listOf(::epsilon))

    private fun processVoid(): String = processTreeToken(TreeToken.VOID)

    private fun arguments(): String {
        if (argumentsDepth > 20) return epsilon()
        argumentsDepth++
        val res = (listOf(::notEmptyArgs, ::epsilon).random())()
        argumentsDepth--
        return res
    }

    private fun argsList(): String =
        concatenate(processTreeToken(TreeToken.LEFTPARENTHESIS), arguments(), processTreeToken(TreeToken.RIGHTPARENTHESIS))
}
