fun work(path: String): String {
    val used = path.length
    return used.toString()
}

class Counter {
    var count: Int = 0
        get() = field + 1
        set(amount) {
            field = amount
        }
    val label: String
        get() = "counter"
    var name: String = ""
        private set
}
