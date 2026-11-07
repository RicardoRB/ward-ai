class Passing {
  int run(int a) {
    if (a == 0) {
      return 0;
    }
    return run(a - 1);
  }
  int getX() {
    return x();
  }
  int x() {
    return 1;
  }
}
