package fixture

class Box {
    var size = 0
}

fun reset(box: Box) {
    box.apply { size }
}
