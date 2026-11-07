package fixture

fun pause() {
    Thread.sleep(100)
}

suspend fun wait() {
    delay(100)
}
