fun run(items: List<Int>, o: Any) {
    val f = { x: Int -> x + 1 }
    val g: (Int) -> Int = { it + 1 }
    items.forEach { item -> println(item) }
    items.apply { size }
    when (val w = compute()) {
        1 -> w
        else -> other
    }
    try {
        risky()
    } catch (e: Exception) {
        throw e
    }
    fun nested(a: Int) = a + f(1)
    nested(g(2))
}

fun String.shout() = uppercase() + suffix
