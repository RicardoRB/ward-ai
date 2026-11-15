final class Strings {
  private Strings() {}

  static String trim(String text) {
    return text.trim();
  }
}

final class Registry {
  private Registry() {}

  private static final class Holder {
    static final int VALUE = 1;
  }
}
