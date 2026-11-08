
class Area {
  long widened(int a, int b, long c) {
    long x = (long) a * b;
    long y = (long) (a * c);
    int z = a * b;
    long w = c * c;
    return x + y + z + w;
  }
}
