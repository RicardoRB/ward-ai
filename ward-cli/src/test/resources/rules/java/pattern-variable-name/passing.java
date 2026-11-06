class Patterns {
  int check(Object o) {
    return switch (o) {
      case String text -> 1;
      default -> 0;
    };
  }
}
