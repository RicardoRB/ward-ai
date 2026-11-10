package dataflow;

class Tries {
  int risky() {
    return 1;
  }

  int tried() {
    int x = 0;
    try {
      x = risky();
    } catch (RuntimeException e) {
      x = -1;
    }
    return x;
  }

  int caught() {
    int value = 0;
    try {
      risky();
    } catch (IllegalStateException e) {
      value = 7;
    }
    return value;
  }
}
