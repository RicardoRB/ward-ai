package fixture

fun next(values: List<Int>): List<Int> = values.map { value -> value + 1 }

fun pair(values: List<Int>, others: List<Int>): List<Int> = values.zip(others) { it, other -> it }
