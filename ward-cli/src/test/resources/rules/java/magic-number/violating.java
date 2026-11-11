import java.util.function.Supplier;

class Violating {
  void method(int a, int[] table) {
    int b = 42;
    if (a > 7) {
      b = b + 3;
    }
    final int indexed = table[4];
    final boolean negated = !(a == 6);
    final Supplier<Integer> supplier = () -> 9;
    final int[] sized = new int[5];
  }
}
