package fixture

fun load(path: String) {
    try {
        read(path)
    } finally {
        cleanup()
    }
}
