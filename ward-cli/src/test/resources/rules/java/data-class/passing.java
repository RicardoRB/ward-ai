class Calculator {
  private int total;

  public int add(int value) {
    return total + value;
  }

  public int subtract(int value) {
    return total - value;
  }

  public int reset() {
    total = 0;
    return total;
  }
}
