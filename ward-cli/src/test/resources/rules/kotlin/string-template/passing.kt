package fixture

val name = "x"
val plain = "Hello $name!"
val braces = "Hello ${name}x"
val nested = "${name.length}"
val text = "a" + name
