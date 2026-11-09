package fixture

fun first(values: List<Int>): Int? {
    for (value in values) {
        if (value < 0) break
        return value
    }
    return null
}
