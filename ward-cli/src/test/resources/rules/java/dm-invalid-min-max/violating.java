final class Clamp {
  int low(int value) {
    return Math.max(Math.min(value, 0), 1);
  }

  int high(int value) {
    return Math.min(Math.max(value, 5), 2);
  }
}
