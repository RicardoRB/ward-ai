package fixture

fun first(values: List<Int>): Int? {
    for (value in values) {
        if (value < 0) break
        return value
    }
    return null
}

fun nested(values: List<Int>, others: List<Int>) {
    for (value in values) {
        for (other in others) {
            if (other < 0) break
        }
        if (value < 0) continue
    }
}
