import org.jspecify.annotations.NullMarked;

@NullMarked
class Returns {
  String first() {
    return null;
  }

  String second(boolean flag) {
    String s = null;
    if (flag) {
      return s;
    }
    return "x";
  }
}
