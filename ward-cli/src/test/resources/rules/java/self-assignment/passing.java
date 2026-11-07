final class Assign {
  private int count;

  void reset(int value) {
    this.count = value;
    count = this.count + value;
  }
}
