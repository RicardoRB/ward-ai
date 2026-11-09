package fixture

fun greet(name: String): String {
    return name.uppercase()
}

fun skip(ignored: Int) = 1

open class Base {
    open fun run(value: Int): Int = 1
}
