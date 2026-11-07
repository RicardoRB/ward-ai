class InReturn {
  int bad(int a) {
    int x = a;
    return x = a + 1;
  }
}
