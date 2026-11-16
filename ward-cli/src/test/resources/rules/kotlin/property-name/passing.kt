package fixture

val userName = "x"

const val MAX_RETRIES = 3

class Hasher {
    val digestSize = 16

    companion object {
        val EMPTY_KEY = ByteArray(0)
        private val DEFAULT_SEED = listOf(1, 2)
    }
}

object Limits {
    val MAX_SEGMENTS = listOf(8, 16)
}
