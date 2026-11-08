package demo.service

/** Prints a stack trace instead of rethrowing with context. */
fun load(path: String) {
    try {
        java.io.File(path).readText()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
