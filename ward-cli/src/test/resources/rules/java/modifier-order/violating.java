final class ModifierOrderViolations {
  static public void run() {}
  final public int value = 1;
  public @Deprecated void late() {}
  @Deprecated public static void old() {}
  private static final int X = 1;
}
