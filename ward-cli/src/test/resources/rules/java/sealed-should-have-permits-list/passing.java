sealed interface Shape permits Circle, Square {
}

sealed interface Vehicle permits Car {
}

final class Car implements Vehicle {
}
