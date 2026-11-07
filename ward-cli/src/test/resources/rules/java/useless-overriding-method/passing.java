class Base {
  void run(int count) {
    System.out.println(count);
  }
}

final class Child extends Base {
  @Override
  void run(int count) {
    System.out.println(count + 1);
    super.run(count);
  }
}
