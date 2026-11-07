class Copies {
  int compute(int a, int b) {
    int x = a + b * 2 - a / 3 + b % 4 - a * b + 7;
    int y = x * x - a + b / 2 + x % 5 - 9;
    int z = y + x - a * 3 + b / 4 - x * 2 + 1;
    int w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5;
    int v = w - z + y * 2 - x + a / 5 + b * 4 - 6;
    int u = v * 2 - w + z / 3 - y + x % 4 + a - b;
    int x = a + b * 5 - a / 6 + b % 7 - a * b + 8;
    int y = x * x - a + b / 9 + x % 2 - 3;
    int z = y + x - a * 4 + b / 8 - x * 6 + 2;
    int w = z * 3 + y - x / 5 + a * 7 - b % 9 + 1;
    int v = w - z + y * 4 - x + a / 2 + b * 6 - 8;
    int u = v * 9 - w + z / 4 - y + x % 3 + a - b;
    return x + y + z + w + v + u;
  }
}
