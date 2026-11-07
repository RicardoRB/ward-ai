final class GoogleRightCurlyPassing {
  void run(boolean a) {
    if (a) {
      run(false);
    } else {
      run(a);
    }
    try {
      run(a);
    } catch (RuntimeException e) {
      run(a);
    } finally {
      run(a);
    }
    if (a) {} else {}
  }
}
