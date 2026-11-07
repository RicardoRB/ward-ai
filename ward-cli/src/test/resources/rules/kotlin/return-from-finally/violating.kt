package fixture

fun load(): Int {
    try {
        return 1
    } finally {
        return 2
    }
}
