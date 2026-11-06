fun work(path: String, items: List<Int>): Int {
    var total = 0
    val (first, second) = items.take(2)
    for (item in items) {
        total += item
    }
    val label = "n$total"
    println(label)
    return total + first
}
