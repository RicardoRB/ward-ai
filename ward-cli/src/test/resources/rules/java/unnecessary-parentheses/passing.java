class Parens {
  int a(int x, int y) {
    int z = (x + y) * 2;
    z = x + (y * 2);
    take(((Object) x).hashCode());
    return z;
  }

  void take(int value) {}
}
