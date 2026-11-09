package fixture

val task = Runnable { println("run") }

val self = object : Runnable {
    override fun run() {
        println(this)
    }
}
