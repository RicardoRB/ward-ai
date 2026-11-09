class Limits {
    const val LIMIT = 7

    fun next(count: Int): Int = if (count < 0) -1 else count + 1

    override fun hashCode(): Int = 17
}
