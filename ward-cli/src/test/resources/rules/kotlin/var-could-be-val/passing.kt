package fixture

fun total(values: List<Int>): Int {
    var sum = 0
    sum = values.size
    return sum
}

open class Shape

class Circle : Shape() {
    var radius: Int = 0
    private var label: String = ""
    private var visible = false

    fun label(label: String) = apply { this.label = label }

    fun visible(): Boolean = visible

    fun show() {
        visible = true
    }
}
