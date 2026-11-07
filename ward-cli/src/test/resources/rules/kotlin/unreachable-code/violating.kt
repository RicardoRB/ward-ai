package fixture

fun run(): Int {
    return 1
    println("never")
}

fun loop() {
    while (true) {
        break
        println("never")
    }
}
