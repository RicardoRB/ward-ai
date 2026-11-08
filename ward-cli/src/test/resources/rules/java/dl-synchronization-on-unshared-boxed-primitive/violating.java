class Locks {
  private final Integer lock = new Integer(0);

  void bad() {
    synchronized (lock) {
      System.out.println();
    }
  }
}
