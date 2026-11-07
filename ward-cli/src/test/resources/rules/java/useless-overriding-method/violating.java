class Base {
  void run(int count) {
    System.out.println(count);
  }
}

final class Child extends Base {
  void run(int count) {
    super.run(count);
  }
}
