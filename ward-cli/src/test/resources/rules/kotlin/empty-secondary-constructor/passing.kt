package fixture

class Box(val size: Int) {
    constructor(other: Box) {
        println(other)
    }
}

class CommentedBox(val size: Int) {
    constructor(other: CommentedBox) {
        // nothing yet
    }
}
