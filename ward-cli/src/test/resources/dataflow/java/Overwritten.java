package dataflow;

class Overwritten {
  int compute() {
    return 1;
  }

  int overwritten() {
    int x = compute();
    x = 2;
    return x;
  }

  int neverRead() {
    int result = 0;
    result = compute();
    return 3;
  }
}
