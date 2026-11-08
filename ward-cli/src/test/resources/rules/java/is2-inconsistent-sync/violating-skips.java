class Plain {
  private int n;

  public synchronized void a() {
    n = 1;
  }

  public synchronized int b() {
    return n;
  }

  public void c() {
    n = 2;
  }

  public int d() {
    return n;
  }
}
