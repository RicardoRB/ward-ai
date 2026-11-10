import org.jspecify.annotations.NullMarked;

@NullMarked
class Pair {
  private String left;
  private String right;

  Pair(String left) {
    this.left = left;
  }

  Pair() {
  }
}
