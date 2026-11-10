package example

expect class Clock {
    fun now(): Long
}

actual class Clock {
    actual fun now(): Long = 0L
}
