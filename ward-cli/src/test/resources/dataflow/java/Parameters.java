package dataflow;

class Parameters {
  int reassigned(int a) {
    a = 3;
    return a;
  }

  int reassignedUnused(int a) {
    a = 3;
    return 2;
  }

  int untouched(int a) {
    return a;
  }
}
