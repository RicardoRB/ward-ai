class Pair(val a: Int) {
    constructor() : this(0)
    fun sum(): Int = a
    constructor(b: Int) : this(b + 1)
}
