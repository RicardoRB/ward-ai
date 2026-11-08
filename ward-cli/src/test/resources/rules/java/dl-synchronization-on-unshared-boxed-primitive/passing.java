class LocksOk {
  private final Object plain = new Object();
  private final Integer shared = Integer.valueOf(0);

  void good() {
    synchronized (plain) {
      System.out.println();
    }
    synchronized (shared) {
      System.out.println();
    }
    synchronized (this) {
      System.out.println();
    }
  }
}
