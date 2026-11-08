package demo.service

/** Greets people by name. */
class Greeter(private val prefix: String) {
    /** Returns the greeting for [name]. */
    fun greet(name: String): String = "$prefix, $name"
}
