package fixture

fun fail(): Nothing {
    throw IllegalStateException("broken")
}
