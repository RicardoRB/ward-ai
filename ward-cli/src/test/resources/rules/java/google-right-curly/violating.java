final class GoogleRightCurlyViolations {
  void run(boolean a) {
    if (a) {
      run(false); }
    if (a) {
      run(a);
    }
    try {
      run(a);
    }
    catch (RuntimeException e) {
      run(a);
    }
  }
}
