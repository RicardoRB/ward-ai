// passes
final class Check {
  void test(String name) {
    synchronized (name) {
      System.out.println(name);
    }
    int hash = System.identityHashCode(name);
  }
}
