package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: Exception) {
        if (e is IOException) {
            retry()
        }
    }
}
