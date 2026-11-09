package fixture

fun close() {
    try {
        println(1)
    } finally {
        println(2)
    }
}

fun commentedFinally() {
    try {
        println(1)
    } finally {
        // nothing yet
    }
}
