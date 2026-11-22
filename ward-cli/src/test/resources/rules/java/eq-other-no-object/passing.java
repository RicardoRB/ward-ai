final class Point {
  public boolean equals(Object other) {
    return other != null;
  }

  static final class Line {
    @Override
    public boolean equals(final Object other) {
      return other != null;
    }
  }

  static final class Dot {
    public boolean equals(final java.lang.Object other) {
      return other != null;
    }
  }
}

final class Builder {
  @Override
  public boolean equals(final Object obj) {
    return obj instanceof Builder && equals((Builder) obj);
  }

  public boolean equals(final Builder other) {
    return other != null;
  }
}
