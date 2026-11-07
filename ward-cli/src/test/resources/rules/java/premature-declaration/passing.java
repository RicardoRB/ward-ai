class Passing {
  int run(String[] strings) {
    int length = 0;
    length += strings.length;
    if (strings == null) {
      return 0;
    }
    return length;
  }
  int other(String[] strings) {
    int n = compute();
    if (strings == null) {
      return 0;
    }
    return n;
  }
  int compute() {
    return 1;
  }
}
