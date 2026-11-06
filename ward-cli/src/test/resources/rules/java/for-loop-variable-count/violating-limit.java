final class Loops {
  int total(int[] values) {
    int sum = 0;
    for (int i = 0, j = values.length - 1; i < j; i++) {
      sum += values[i];
    }
    return sum;
  }
}
