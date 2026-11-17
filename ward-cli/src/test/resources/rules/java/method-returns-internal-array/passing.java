final class Holder {
  private final int[] values = new int[3];

  int[] values() {
    return values.clone();
  }
}

final class EmptyConstants {
  private static final String[] NONE = new String[0];
  private final int[] nothing = {};

  String[] names() {
    return NONE;
  }

  int[] values() {
    return nothing;
  }
}
