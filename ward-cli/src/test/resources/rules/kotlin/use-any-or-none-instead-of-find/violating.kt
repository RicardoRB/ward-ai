package fixture

fun hasNegative(values: List<Int>): Boolean = values.find { it < 0 } != null
