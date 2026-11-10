package dataflow;

class Branches {
  int merged(boolean flag) {
    int x = 0;
    if (flag) {
      x = 1;
    } else {
      x = 2;
    }
    return x;
  }

  int oneBranch(boolean flag) {
    int y = 0;
    if (flag) {
      y = 5;
    }
    return y;
  }

  int unusedInBranch(boolean flag) {
    int z = 0;
    if (flag) {
      z = 9;
      return 0;
    }
    return z;
  }
}
