// passes
final class Check {
  int test(boolean flag) {
    String name = null;
    if (flag) {
      name = "x";
    }
    if (name != null) {
      return name.length();
    }
    String other = "y";
    return other.length();
  }
}
