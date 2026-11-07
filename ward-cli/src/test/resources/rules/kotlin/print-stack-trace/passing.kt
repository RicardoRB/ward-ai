package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: Exception) {
        logger.warn("failed", e)
    }
}
