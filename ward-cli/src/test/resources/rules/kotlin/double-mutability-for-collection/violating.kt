package fixture

class Registry {
    val items: MutableList<String> = mutableListOf()
    val byName = mutableMapOf<String, Int>()
    private val hidden: MutableList<String> = mutableListOf()
    fun local(): Int {
        val inner: MutableList<Int> = mutableListOf()
        return inner.size
    }
}
