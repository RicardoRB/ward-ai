package fixture

fun check(a: Boolean, b: Boolean, c: Boolean) {
    if (a && b && c) {
        println(1)
    }
}

fun lambdaCase(xs: List<Item>, y: Boolean) {
    if (xs.any { it.a && it.b && it.c } && y) {
        println(1)
    }
}
