sealed interface Shape permits Circle, Square {
}

sealed interface Vehicle {
}

final class Circle implements Vehicle {
}
