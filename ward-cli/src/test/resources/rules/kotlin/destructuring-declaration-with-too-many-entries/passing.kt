package fixture

fun work(pair: Pair<Int, Int>) {
    val (a, b, c) = listOf(1, 2, 3)
    val (x, y) = pair
    println(a + b + c + x + y)
}
