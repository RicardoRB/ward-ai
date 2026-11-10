enum Color {
  RED,
  BLUE
}

final class Pick {
  int code(Color color) {
    switch (color) {
      case RED:
        return 1;
      case BLUE:
        return 2;
      default:
        return 0;
    }
  }
}
