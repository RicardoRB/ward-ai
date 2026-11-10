final class Check {
  private int total;

  void keep(int value) {
    total = value;
  }

  void call(int value) {
    System.out.println(value);
  }

  void empty() {}

  int pure(int value) {
    int doubled = value * 2;
    return doubled;
  }
}
