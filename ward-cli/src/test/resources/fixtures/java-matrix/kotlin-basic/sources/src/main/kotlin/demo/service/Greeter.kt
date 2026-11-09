package demo.service

/** Greets people by name. */
class Greeter(private val prefix: String) {
    /**
     * Returns the greeting for a person.
     *
     * @param name the person's name
     * @return the greeting
     */
    fun greet(name: String): String = "$prefix, $name"
}
