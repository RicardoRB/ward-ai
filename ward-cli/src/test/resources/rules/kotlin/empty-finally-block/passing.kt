package fixture

fun close() {
    try {
        println(1)
    } finally {
        println(2)
    }
}
