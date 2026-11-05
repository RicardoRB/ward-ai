package dataflow;

class Increments {
  int bump() {
    int k = 0;
    k++;
    k += 2;
    return 1;
  }

  int chained(int base) {
    int m = base;
    m = m + 1;
    m++;
    return m;
  }

  int prefix() {
    int p = 0;
    ++p;
    return p;
  }
}
