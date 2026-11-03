package fixture

fun deep(a: Int, b: Int): Int {
    for (i in 0..a) {
        if (i > 0) {
            while (b > 0) {
                if (a > b && b > 0) {
                    when (a) {
                        1 -> return 1
                    }
                }
                if (a < b) {
                    return 2
                }
            }
        }
    }
    return 0
}
