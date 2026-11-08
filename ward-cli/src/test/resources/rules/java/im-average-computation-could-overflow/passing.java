
class Middle {
  int mid(int low, int high, long a, long b) {
    int x = low + (high - low) / 2;
    int y = (low + high) / 3;
    long z = (a + b) / 2;
    int w = (low + 1) / 2;
    return x + y + (int) z + w;
  }
}
