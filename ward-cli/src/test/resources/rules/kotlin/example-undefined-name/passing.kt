import java.util.Locale

class Sizes(val base: Int, step: Int) {
    var count = base + step

    fun total(items: List<Int>, bound: Int = 1): Int {
        var sum = bound
        for (item in items) {
            sum += item
        }
        val doubled = items.map { it * 2 }
        println(doubled)
        return sum + count + Other.VALUE
    }
}

fun String.shout() = uppercase(Locale.ROOT) + suffix
