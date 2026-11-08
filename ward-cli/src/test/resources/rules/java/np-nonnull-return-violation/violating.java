final class Check {
  @javax.annotation.Nonnull
  String direct() {
    return null;
  }

  @javax.annotation.Nonnull
  String maybe(boolean flag) {
    String result = null;
    if (flag) {
      result = "x";
    }
    return result;
  }
}
