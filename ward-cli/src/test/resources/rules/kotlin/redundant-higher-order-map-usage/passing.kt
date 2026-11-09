package fixture

fun copy(values: List<Int>): List<Int> = values.map { it * 2 }

fun pairs(): List<Pair<Int, Int>> = mapOf(1 to 2).map { it }
