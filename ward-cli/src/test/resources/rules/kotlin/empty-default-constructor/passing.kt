package fixture

class Bare

class WithParam(val x: Int)

class Private private constructor()

class Annotated @Inject constructor()

expect class Platform()

actual class Platform()

class WithThis() {
    constructor(x: Int) : this()
}
