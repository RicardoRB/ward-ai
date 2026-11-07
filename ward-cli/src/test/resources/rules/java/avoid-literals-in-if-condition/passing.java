class Counter {
  int count;

  void check(int max) {
    if (count > 0 && count < max) {
      count = 3;
    }
  }
}
