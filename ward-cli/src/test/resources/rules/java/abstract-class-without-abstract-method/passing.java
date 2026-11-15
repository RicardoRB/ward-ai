abstract class Shape {
  abstract double area();
}

abstract class Base extends Thread {
  void helper() {}
}

abstract class Shared implements Runnable {
  void helper() {}
}
