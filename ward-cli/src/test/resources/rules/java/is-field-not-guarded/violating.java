import javax.annotation.concurrent.GuardedBy;

class Counter {
  @GuardedBy("this")
  private int count;

  public int read() {
    return count;
  }
}
