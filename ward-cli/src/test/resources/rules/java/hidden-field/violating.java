class Violating {
  private int count;
  private static int total;

  void run(int count) {
    int total = count;
    System.out.println(total);
  }

  void set(int count) {
    this.count = count;
  }
}
