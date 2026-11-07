package fixture

class Point(val x: Int) {
    override fun equals(other: Any?): Boolean = other is Point && other.x == x
}
