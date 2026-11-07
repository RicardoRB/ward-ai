import org.jspecify.annotations.NullMarked;

@NullMarked
class Inference {
  void run() {
    List<String> names = List.of("a", null);
    List<String> more = Arrays.asList(null, "b");
  }
}
