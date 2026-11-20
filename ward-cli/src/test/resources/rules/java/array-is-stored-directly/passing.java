final class Holder {
  private int[] values;

  Holder(int[] input) {
    this.values = input.clone();
  }
}

final class Parsed {
  private final int[] points;

  private Parsed(int[] points) {
    this.points = points;
  }

  static Parsed of(int[] points) {
    return new Parsed(points.clone());
  }
}

interface Provider<T> {
  T get();
}

final class Lazy {
  private final Provider<byte[]> provider;

  Lazy(Provider<byte[]> provider) {
    this.provider = provider;
  }
}
