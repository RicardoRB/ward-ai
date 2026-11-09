package fixture

fun risky() {
    try {
        println(1)
    } catch (e: Exception) {
        println(e)
    }
}
