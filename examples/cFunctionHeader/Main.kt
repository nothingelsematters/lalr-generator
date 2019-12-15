package cFunctionHeader

fun main() {
    try {
        println(CFunctionHeaderParser().parse(readLine()!!.byteInputStream()))
    } catch (e: Exception) {
        println("FAILURE: ${e.message}")
    }
}
