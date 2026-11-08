class BoxingOk {
  double good(int count) {
    return (double) count;
  }

  int same(int count) {
    return Integer.valueOf(count).intValue();
  }
}
