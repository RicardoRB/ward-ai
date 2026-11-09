class Limits {
    val maxSize = 42
    const val LIMIT = 7

    fun check(count: Int): Boolean {
        val threshold = count * 3
        return threshold > -5 && count != 0 && count != 1
    }

    override fun hashCode(): Int = 17
}
