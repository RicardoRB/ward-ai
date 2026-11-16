package fixture

fun greet(name: String): String {
    return name.uppercase()
}

fun skip(ignored: Int) = 1

open class Base {
    open fun run(value: Int): Int = 1
}

interface Store {
    fun save(key: String, content: ByteArray)
    fun load(key: String, fallback: String = ""): String
}

internal external fun nativeOpen(path: String, flags: Int): Int

class Holder {
    var path: String = ""
        private set
    var mode: Int
        get() = 0
        set(_) = Unit
}
