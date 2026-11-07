final class Clamp {
  int low(int value) {
    return Math.max(Math.min(value, 1), 0);
  }

  int high(int value) {
    return Math.min(Math.max(value, 0), 1);
  }
}
