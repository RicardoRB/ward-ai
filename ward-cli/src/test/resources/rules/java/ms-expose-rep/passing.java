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
