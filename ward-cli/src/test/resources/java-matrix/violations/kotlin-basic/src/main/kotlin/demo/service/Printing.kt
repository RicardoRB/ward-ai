package demo.service

/** A greeting that may be a const val (#294 may-be-const). */
val GREETING = "hello"

/** Reads files the unsafe way. */
class Printing {
    /**
     * Prints a stack trace instead of rethrowing with context.
     *
     * @param path the file to read, or null
     */
    fun load(path: String?) {
        val name = path!!
        try {
            java.io.File(name).readText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
