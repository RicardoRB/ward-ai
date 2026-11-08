// passes
final class Check {
  String plain() {
    return null;
  }

  @javax.annotation.Nonnull
  String fine(boolean flag) {
    String result = null;
    if (flag) {
      result = "x";
    } else {
      result = "y";
    }
    return result;
  }
}
