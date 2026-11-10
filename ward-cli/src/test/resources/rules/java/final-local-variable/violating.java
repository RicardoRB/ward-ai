class Violating {
  int run(int input) {
    int first = input;
    int second = 2;
    second = second + 1;
    final int third = 3;
    return first + second + third;
  }
}
