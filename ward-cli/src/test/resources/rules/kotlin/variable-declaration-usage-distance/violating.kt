fun total(values: List<Int>): Int {
    var sum = 0
    val first = values.first()
    val second = values.last()
    val third = values.size
    val fourth = values.count()
    return sum + first + second + third + fourth
}
