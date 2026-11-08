class Counter {
  private int count;
  private int other;

  public synchronized void inc() {
    count++;
    bump();
  }

  public synchronized int get() {
    return count;
  }

  public synchronized void reset() {
    count = 0;
  }

  public void unsafe() {
    count = count + 1;
  }

  private void bump() {
    count += 2;
  }
}
