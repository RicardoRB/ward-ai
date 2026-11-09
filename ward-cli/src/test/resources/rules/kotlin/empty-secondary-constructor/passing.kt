package fixture

class Box(val size: Int) {
    constructor(other: Box) {
        println(other)
    }
}
