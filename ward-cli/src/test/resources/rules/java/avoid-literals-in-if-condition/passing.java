class Counter {
  int count;

  void check(int max, String name) {
    if (count > 0 && count < max) {
      count = 3;
    }
    if (count != -1 && name != null) {
      count = 2;
    }
    if (max > 0 && count == 3) {
      count = 4;
    }
    if (count + 1 > max) {
      count = 5;
    }
    if (name.equals("none")) {
      count = 6;
    }
    if (count < max == true) {
      count = 7;
    }
  }
}
