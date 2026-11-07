package fixture

fun check(value: Int) {
    if (value < 0) {
        throw IllegalStateException("negative")
    }
    if (value > 10) {
        throw Exception("too large")
    }
    if (value == 5) {
        throw IllegalStateException("five")
    }
}
