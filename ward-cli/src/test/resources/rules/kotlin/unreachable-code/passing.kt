package fixture

fun run(): Int {
    println("first")
    return 1
}

fun loop() {
    while (true) {
        println("again")
        break
    }
}
