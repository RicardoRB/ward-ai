package fixture

fun greeting(): String {
    return "hello"
}

fun fail(): Nothing {
    throw IllegalStateException()
}
