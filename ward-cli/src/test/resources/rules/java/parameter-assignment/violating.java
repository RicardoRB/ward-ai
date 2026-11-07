class Violating {
  void run(int size) {
    size = size + 1;
    size += 2;
    int local = size;
    local = 3;
  }

  void other(int size) {
    int copy = size;
    this.value = size;
  }

  int value;
}
