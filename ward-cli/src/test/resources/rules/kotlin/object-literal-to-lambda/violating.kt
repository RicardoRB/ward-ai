package fixture

val task = object : Runnable {
    override fun run() {
        println("run")
    }
}
