package fixture

class Point(val x: Int) {
    fun equals(other: Point): Boolean = other.x == x
}
