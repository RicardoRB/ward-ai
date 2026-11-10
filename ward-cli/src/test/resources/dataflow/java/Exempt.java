package dataflow;

class Exempt {
  int compute() {
    return 1;
  }

  int ignoredName() {
    int ignoredValue = compute();
    ignoredValue = 3;
    return 0;
  }

  int unusedName() {
    int unusedValue = compute();
    return 0;
  }
}
