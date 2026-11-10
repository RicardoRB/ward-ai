package fixture

fun hasEvens(values: List<Int>): Boolean = values.filter { it % 2 == 0 }.isNotEmpty()
