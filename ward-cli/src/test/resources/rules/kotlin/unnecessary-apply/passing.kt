package fixture

class Box {
    var size = 0
}

fun make(): Box {
    val box = Box().apply { size = 1 }
    return Box().apply { size = 2 }
}
