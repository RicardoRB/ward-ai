package fixture

class Registry {
    val items: List<String> = listOf()
    val byName = mapOf<String, Int>()
    private val hidden: MutableList<String> = mutableListOf()
    fun local(): Int {
        val inner: MutableList<Int> = mutableListOf()
        return inner.size
    }
}

class Roots {
    val all: List<String>
        get() {
            val result = mutableListOf<String>()
            result.add("root")
            return result
        }

    init {
        val seen = mutableSetOf<String>()
        println(seen)
    }
}
