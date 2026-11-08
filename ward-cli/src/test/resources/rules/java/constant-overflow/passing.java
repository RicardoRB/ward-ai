final class T {
  long run(int x) {
    long day = 1000L * 60 * 60 * 24 * 365;
    return day + x + (60 * 60) + Integer.MAX_VALUE + -2147483648;
  }
}
