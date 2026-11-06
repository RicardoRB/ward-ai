import java.util.function.Supplier;
class Passing {
  Supplier<Integer> run() {
    return () -> 1;
  }
}
