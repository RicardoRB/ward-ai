package fixture

class Widget(val size: Int)

fun Widget(): Widget = Widget(0)

fun Widget(size: String): Widget = Widget(size.length)

private fun Widget(size: Int, wide: Boolean = true) = Widget(size).also { println(wide) }
