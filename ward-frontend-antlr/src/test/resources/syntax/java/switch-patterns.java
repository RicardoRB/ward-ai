package example;

public class Describe {

    String describe(Object value) {
        return switch (value) {
            case null -> "null";
            case Integer i when i > 10 -> "big integer " + i;
            case Integer i -> "integer " + i;
            case String s -> "string " + s;
            default -> "other";
        };
    }
}
