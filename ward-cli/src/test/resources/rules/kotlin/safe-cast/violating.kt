package fixture

fun name(value: Any): String? = if (value is String) value else null
