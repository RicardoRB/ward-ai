final class Nested {
  int deep(int a, int b, int c, int d, int e) {
    if (a > 0) {
      if (b > 0) {
        if (c > 0) {
          if (d > 0) {
            if (e > 0 && a < b || c < d) {
              return 1;
            }
          }
        }
      }
    }
    return 0;
  }
}
