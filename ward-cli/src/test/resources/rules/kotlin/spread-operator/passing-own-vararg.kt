package fixture

fun join(vararg values: String): List<String> = listOf(*values)

class Joiner {
    fun all(prefix: String, vararg parts: String): List<String> = listOf(prefix, *parts)
}
