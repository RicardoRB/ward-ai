record P(int x) {
  P {
    int y = x;
  }

  int twice() {
    return x() * 2;
  }
}
