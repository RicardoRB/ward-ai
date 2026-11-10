package fixture

fun run(block: () -> Unit) = block()

fun use() {
    run() { println("x") }
    listOf(1).forEach() { println(it) }
}
