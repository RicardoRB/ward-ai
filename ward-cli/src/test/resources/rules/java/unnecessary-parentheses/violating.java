class Parens {
  int a(int x, int y) {
    int z = (x);
    int w = (x + y);
    z = (y);
    take((x));
    return (z);
  }

  void take(int value) {}
}
