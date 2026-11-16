package fixture

fun run(block: () -> Unit) = block()

fun use() {
    run { println("x") }
    listOf(1).forEach { println(it) }
}

open class Person

class Vet : Person() {
    val name = "vet"
}

object Registry : Person() {
    val size = 0
}

val anonymous = object : Person() {
    val size = 1
}
