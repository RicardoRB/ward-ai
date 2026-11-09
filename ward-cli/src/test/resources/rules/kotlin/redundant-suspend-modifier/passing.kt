import kotlinx.coroutines.delay

suspend fun pause(): Int {
    delay(1)
    return 1
}
