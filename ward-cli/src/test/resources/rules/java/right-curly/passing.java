final class RightCurlyPassing {
  void run(boolean a) {
    if (a) {
      run(a);
    } else {
      run(a);
    }
  }
}
