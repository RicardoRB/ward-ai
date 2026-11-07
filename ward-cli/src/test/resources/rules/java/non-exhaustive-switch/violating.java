enum Color {
  RED,
  BLUE
}

final class Pick {
  int code(Color color) {
    switch (color) {
      case RED:
        return 1;
    }
    return 0;
  }
}
