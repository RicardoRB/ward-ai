final class NeedBracesPassing {
  void run(boolean a) {
    if (a) {
      run(false);
    } else if (!a) {
      run(true);
    } else {
      run(a);
    }
    for (int i = 0; i < 2; i++) {
      run(a);
    }
    while (a) {
      a = false;
    }
  }
}
