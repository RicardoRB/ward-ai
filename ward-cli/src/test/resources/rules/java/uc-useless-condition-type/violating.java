final class Check {
  boolean test(byte value, char letter) {
    return value > 127 || letter >= 0;
  }
}
