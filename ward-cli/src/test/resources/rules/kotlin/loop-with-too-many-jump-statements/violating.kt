package fixture

fun first(values: List<Int>): Int? {
    for (value in values) {
        if (value < 0) break
        if (value > 9) continue
        return value
    }
    return null
}
