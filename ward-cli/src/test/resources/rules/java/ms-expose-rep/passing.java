final class T {
  private static final int[] DATA = {1};
  private final int[] own = {1};

  public static int[] data() {
    return DATA.clone();
  }

  int[] instance() {
    return own;
  }

  static int[] hidden() {
    return DATA;
  }
}

final class EmptyConstants {
  private static final String[] NONE = new String[0];
  private static final int[] NOTHING = {};

  public static String[] names() {
    return NONE;
  }

  public static int[] values() {
    return NOTHING;
  }
}
