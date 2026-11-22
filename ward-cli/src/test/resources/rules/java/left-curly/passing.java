final class LeftCurlyPassing {
  void run(boolean a) {
    if (a) {
      run(a);
    }
    int[] values = {1, 2};
  }

  @Names(
      {
          "first-name-of-the-list",
          "second-name-of-the-list"
      }
  )
  static final class Annotated {}
}
