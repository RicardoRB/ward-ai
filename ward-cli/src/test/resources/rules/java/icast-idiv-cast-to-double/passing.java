
class Ratio {
  double ratio(int a, double b, long c, int d) {
    double x = (double) a / d;
    double y = (double) (a / b);
    long z = (long) (c / d);
    return x + y + z;
  }
}
