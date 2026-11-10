package fixture

fun work(pair: Pair<Int, Int>) {
    val (a, b, c, d) = listOf(1, 2, 3, 4)
    val (x, y) = pair
    println(a + b + c + d + x + y)
}
