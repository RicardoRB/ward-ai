fun classify(value: Int): String {
    when (value) {
        0 -> return "zero"
        in 1..9 -> {
            if (value % 2 == 0) {
                return "small even"
            } else {
                return "small odd"
            }
        }
        else -> {
            for (i in 0 until value) {
                    println(i)
            }
        }
    }
    return "big"
}
