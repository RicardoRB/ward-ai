final class Check {
  private int value;

  synchronized void setValue(int value) {
    this.value = value;
  }

  int getValue() {
    return value;
  }
}
