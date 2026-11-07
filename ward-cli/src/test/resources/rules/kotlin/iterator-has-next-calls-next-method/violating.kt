package fixture

class Bad(private val items: List<Int>) : Iterator<Int> {
    private var index = 0
    override fun hasNext(): Boolean {
        return items.size > index && next() > 0
    }
    override fun next(): Int = items[index++]
}
