package fixture

fun join(vararg values: String, other: Array<String>): List<String> = listOf(*values, *other)
