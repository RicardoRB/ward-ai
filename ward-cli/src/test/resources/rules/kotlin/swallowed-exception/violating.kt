package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: java.io.IOException) {
        println("failed")
    }
}
