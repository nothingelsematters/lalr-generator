fun main() {
    try {
        println(CalculatorParser().parse(readLine()!!.byteInputStream()))
    } catch (e: Exception) {
        println("FAILURE: ${e.message}")
    }
}
