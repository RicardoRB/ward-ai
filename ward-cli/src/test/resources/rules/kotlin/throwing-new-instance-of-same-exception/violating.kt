package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: IllegalStateException) {
        throw IllegalStateException(e)
    }
}
