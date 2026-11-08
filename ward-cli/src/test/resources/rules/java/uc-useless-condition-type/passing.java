final class Check {
  boolean test(byte value, char letter, int number) {
    return value > 100 || letter > 0 || number > 127;
  }
}
