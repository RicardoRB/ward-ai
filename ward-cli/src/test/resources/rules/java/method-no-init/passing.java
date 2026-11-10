import org.jspecify.annotations.NullMarked;

@NullMarked
class Pair {
  private String left;
  private String right;

  Pair(String left, String right) {
    this.left = left;
    this.right = right;
  }

  Pair(String both) {
    this(both, both);
  }

  Pair() {
    init();
  }

  @Initializer
  void init() {
    left = "";
    right = "";
  }
}
