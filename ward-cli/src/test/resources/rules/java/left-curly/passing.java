final class LeftCurlyPassing {
  void run(boolean a) {
    if (a) {
      run(a);
    }
    int[] values = {1, 2};
  }
}
