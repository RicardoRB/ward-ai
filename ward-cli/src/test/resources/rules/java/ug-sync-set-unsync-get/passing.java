final class Check {
  private int value;
  private volatile int count;

  synchronized void setValue(int value) {
    this.value = value;
  }

  synchronized int getValue() {
    return value;
  }

  synchronized void setCount(int count) {
    this.count = count;
  }

  int getCount() {
    return count;
  }
}
