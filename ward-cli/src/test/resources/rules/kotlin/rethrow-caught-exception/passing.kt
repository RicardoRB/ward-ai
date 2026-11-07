package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: Exception) {
        throw IllegalStateException("failed", e)
    }
}
