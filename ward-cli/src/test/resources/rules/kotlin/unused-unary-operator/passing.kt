package fixture

fun sum(): Int {
    val a = 1 + 2 + 3
    val b = -a
    return listOf(b).map { -it }.first()
}
