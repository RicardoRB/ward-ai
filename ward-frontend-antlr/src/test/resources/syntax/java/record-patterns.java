package example;

public class Geometry {

    double area(Object shape) {
        if (shape instanceof Rectangle(Point(var x1, var y1), Point(var x2, var y2))) {
            return Math.abs((x2 - x1) * (y2 - y1));
        }
        return switch (shape) {
            case Rectangle(Point origin, Point corner) when origin.equals(corner) -> 0.0;
            case Rectangle(Point origin, Point corner) -> 1.0;
            default -> -1.0;
        };
    }
}
