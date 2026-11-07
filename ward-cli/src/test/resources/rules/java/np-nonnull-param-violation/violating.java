import org.jspecify.annotations.NullMarked;

@NullMarked
class Calls {
  Calls(String label) {}

  void take(String value) {}

  void run() {
    take(null);
    String missing = null;
    take(missing);
    new Calls(null);
  }
}
