package fixture

fun check(value: Int) {
    if (value < 0) {
        throw IllegalStateException()
    }
    if (value > 10) {
        throw Exception()
    }
    if (value == 5) {
        throw IllegalStateException("five")
    }
}
