package fixture

fun first(values: List<Int>): Int? {
    for (value in values) {
        if (value < 0) break
        if (value > 9) continue
        if (value == 5) break
        return value
    }
    return null
}
