package fixtures.modern;

import java.util.List;

sealed interface Shape permits Circle, Square {}

record Circle(double radius) implements Shape {}

record Square(double side) implements Shape {}

final class Modern {
  String describe(final Shape shape) {
    return switch (shape) {
      case Circle circle when circle.radius() > 1 -> "big circle";
      case Circle circle -> "circle";
      case Square square -> "square";
    };
  }

  String banner(final List<String> names) {
    var text = """
        names: %s
        """;
    return text.formatted(names);
  }
}
