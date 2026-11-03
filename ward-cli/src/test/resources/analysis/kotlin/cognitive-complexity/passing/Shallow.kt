package fixture

fun shallow(a: Int, b: Int): Int {
    if (a > b) {
        return a
    }
    return b
}
