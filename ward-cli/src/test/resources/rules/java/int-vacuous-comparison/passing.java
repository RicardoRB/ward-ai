final class Passing {
  boolean run(int x) {
    return x >= 0;
  }
}

final class Wide {
  boolean fits(long value, int other) {
    long sum = value + other;
    return value > Integer.MAX_VALUE || sum < Integer.MIN_VALUE;
  }
}
