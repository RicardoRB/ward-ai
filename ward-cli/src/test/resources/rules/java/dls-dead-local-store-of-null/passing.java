class NullsOk {
  Object compute() {
    return new Object();
  }

  void good() {
    Object o = compute();
    o = compute();
    String s = null;
    System.out.println(s);
  }
}
