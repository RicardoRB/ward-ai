class Unused {
  int run(int value) {
    int used = value * 2;
    return used;
  }
}

class Patterns {
  sealed interface Shape permits Num, Str {}

  record Num(int value) implements Shape {}

  record Str(String text) implements Shape {}

  int size(Shape shape) {
    return switch (shape) {
      case Num num -> 1;
      case Str str -> 2;
    };
  }

  int record(Shape shape) {
    return switch (shape) {
      case Num(int value) -> 1;
      case Str(String text) -> 2;
    };
  }
}
