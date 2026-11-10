package fixture

fun names(values: List<String>?): List<String> = values.orEmpty()

fun empty(text: String?): Boolean = text.isNullOrEmpty()

fun all(first: Int?): List<Int?> = listOfNotNull(first, 2)
