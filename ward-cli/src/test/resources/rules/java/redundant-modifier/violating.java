interface Api {
  public void run();
  abstract void stop();
  public static final int LIMIT = 1;
}
class Outer {
  static enum Mode { ON }
}
