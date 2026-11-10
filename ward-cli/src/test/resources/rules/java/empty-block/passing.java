final class EmptyBlockPassing {
  void run(boolean a) {
    if (a) {
      run(a);
    }
  }
}
