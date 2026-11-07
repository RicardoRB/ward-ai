package fixture

fun load(path: String) {
    try {
        read(path)
    } catch (e: java.io.IOException) {
        println(e)
    }
    try {
        read(path)
    } catch (ignored: java.io.IOException) {
        println("skipped")
    }
    try {
        parse(path)
    } catch (e: NumberFormatException) {
        println("not a number")
    }
    try {
        read(path)
    } catch (e: java.io.IOException) {
        throw e
    }
}
