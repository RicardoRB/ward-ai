package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
