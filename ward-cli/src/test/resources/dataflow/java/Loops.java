package dataflow;

class Loops {
  int sum(int n) {
    int total = 0;
    for (int i = 0; i < n; i++) {
      total += i;
    }
    return total;
  }

  int lastValue(int n) {
    int last = 0;
    int i = 0;
    while (i < n) {
      last = i;
      i++;
    }
    return i;
  }

  int counter(int n) {
    int count = 0;
    do {
      count = count + 1;
    } while (count < n);
    return count;
  }
}
