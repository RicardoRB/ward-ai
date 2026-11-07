package fixture

fun shout(name: String): String = name.uppercase()
fun title(value: String): String = String.format(Locale.ROOT, "%s!", value)
