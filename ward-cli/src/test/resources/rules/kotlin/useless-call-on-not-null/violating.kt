package fixture

fun names(): List<String> = listOf("a").orEmpty()

fun empty(): Boolean = "text".isNullOrEmpty()

fun all(): List<Int> = listOfNotNull(1, 2)
