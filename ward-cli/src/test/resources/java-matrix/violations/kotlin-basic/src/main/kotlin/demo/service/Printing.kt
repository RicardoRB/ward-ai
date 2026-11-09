package demo.service

/** A greeting that may be a const val (#294 may-be-const). */
val GREETING = "hello"

/** Prints a stack trace instead of rethrowing with context. */
fun load(path: String?) {
    try {
        java.io.File(path!!).readText()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
