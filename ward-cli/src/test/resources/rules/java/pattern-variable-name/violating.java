class Patterns {
  int check(Object o) {
    return switch (o) {
      case String Text -> 1;
      default -> 0;
    };
  }
}
