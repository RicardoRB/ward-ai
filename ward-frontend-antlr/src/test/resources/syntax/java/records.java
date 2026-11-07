package example;

public record Point(int x, int y) implements Comparable<Point> {

    public Point {
        if (x < 0) {
            throw new IllegalArgumentException("negative");
        }
    }

    public static Point origin() {
        return new Point(0, 0);
    }

    @Override
    public int compareTo(Point other) {
        return Integer.compare(x, other.x);
    }
}
