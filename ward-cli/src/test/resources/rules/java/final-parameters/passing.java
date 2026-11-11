abstract class Params {
  void run(final int a, int b) {
    b = a;
  }

  abstract void later(int c);

  void unused(int d) {}

  void lambda() {
    java.util.function.IntUnaryOperator op = value -> value;
  }
}
