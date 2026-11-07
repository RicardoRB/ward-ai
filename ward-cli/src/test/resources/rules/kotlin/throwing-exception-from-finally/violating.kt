package fixture

fun load(path: String) {
    try {
        read(path)
    } finally {
        throw IllegalStateException("cleanup failed")
    }
}
