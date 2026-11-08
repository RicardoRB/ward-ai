final class Check {
  void test(Integer count) {
    synchronized (count) {
      System.out.println(count);
    }
    int hash = System.identityHashCode(count);
  }
}
