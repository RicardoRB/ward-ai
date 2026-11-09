package fixture

fun outer(total: Int) {
    val count = 1
    if (total > 0) {
        val count = 2
        println(count)
    }
    listOf(1).forEach { total -> println(total) }
    val (first, second) = Pair(1, 2)
    run {
        val first = first
        println(first + second)
    }
}
