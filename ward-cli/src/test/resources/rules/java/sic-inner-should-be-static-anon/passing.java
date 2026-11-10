final class T {
  private int value;

  Runnable use() {
    return new Runnable() {
      public void run() {
        value++;
      }
    };
  }

  static Runnable make() {
    return new Runnable() {
      public void run() {}
    };
  }
}
