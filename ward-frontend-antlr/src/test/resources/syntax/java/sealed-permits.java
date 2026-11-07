package example;

public sealed interface Shape permits Circle, Square, Polygon {
}

public record Circle(double radius) implements Shape {
}

public record Square(double side) implements Shape {
}

public non-sealed class Polygon implements Shape {
}

public final class Triangle extends Polygon {
}
