class Counter {
  int count;

  void check(char c, String name) {
    if (count > 3) {
      count++;
    }
    if (c == 'x') {
      count--;
    }
    if (name == "none") {
      count = 0;
    }
    if ((count == 7)) {
      count = 1;
    }
  }
}
