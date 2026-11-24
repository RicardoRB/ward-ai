package fixture

expect open class Blob
internal constructor(data: ByteArray) {
    internal val data: ByteArray
}

actual open class Chunk
internal actual constructor(
    internal actual val data: ByteArray,
) : Comparable<Chunk> {
    actual override fun compareTo(other: Chunk): Int = data.size - other.data.size
}

actual abstract class Forwarder actual constructor(
    @get:JvmName("delegate")
    actual val delegate: Reader,
    value: Int,
) : Reader

class Vet : Person() {
    var speciality: String? = null

    /** Counts the letters. */
    fun letters(): Int = speciality?.length ?: 0
}

class Single { val one = 1 }

class Empty { fun nothing() {} }

class Clock {
    constructor() : this(0L)

    /** After a constructor without a body. */
    constructor(start: Long)
}

enum class Level {
    /** The first entry. */
    Low {
        override fun next(): Level = High
    },
    High {
        override fun next(): Level = Low
    };

    abstract fun next(): Level
}

fun unsigned(target: Sink) {
    target.writeByte(254u.toUByte())
    val wide = 0xFFuL + 3UL + 1U
    println(wide)
}

fun assign(table: MutableMap<String, Any>, key: String) {
    table[key] =
        Entry(created = now())
}

fun cast(input: Any): String? =
    lookup("name") as String?

fun choose(kind: Any): Boolean {
    if (kind is String) return kind.isEmpty()
    return when (kind::class.simpleName) {
        "First",
        "Second",
        -> false
        else -> true
    }
}

fun lambdas(buffer: Reader): Pipe {
    val twice = fun(amount: Int): Int { return amount * 2 }
    val extended = fun Int.(other: Int) = this + other
    println(twice(1) + 1.extended(2))
    return Pipe(
        buffer,
        object : Reader by buffer {
            override fun close() {
                first()
                second()
            }
        },
        retry(times = 2) { attempt ->
            first()
            if (attempt is Int) second()
        },
    )
}

fun template(items: List<Any>, flag: Boolean): String =
    "${items.map { item ->
        item as String
    }} ${if (flag) this else items} ${items is List<*>}"

fun describe(value: Any?): String =
    value?.let { "ok" } ?: error("The ${value::class::simpleName} value is not supported")

fun Route.timing() {
    fun nowMillis(): Float {
        return System.nanoTime() / 1000_000f
    }

    get("/clock") {
        val delay = parameters["delay"]?.toLongOrNull() ?: 2
        respond(nowMillis() + delay)
    }
}

fun drip(count: Int, clock: () -> Float) {
    for (i in 0 ..< count) {
        println(clock() + i)
    }
    val first = 0..<count
    println(first)
}
