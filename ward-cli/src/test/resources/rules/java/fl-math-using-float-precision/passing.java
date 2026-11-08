// passes
final class FloatMath {
  float add(float a, float b) {
    return a + b;
  }
}
final class Other {
  double add(double a, double b) {
    return a + b;
  }
  float sum(float a, float b) {
    return 1.5f * 2;
  }
  int count(int a, int b) {
    return a + b;
  }
  float boxed(Float a) {
    return a - 1;
  }
  float viaFloat(float a) {
    return a * 2;
  }
}
