final class Numbers {
  boolean nan(double value) {
    return value == Double.NaN;
  }

  boolean other(float value) {
    return Float.NaN != value;
  }
}
