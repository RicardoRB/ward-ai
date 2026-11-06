class Patterns {
  int check(Object o) {
    return switch (o) {
      case String s -> 1;
      default -> 0;
    };
  }
}
