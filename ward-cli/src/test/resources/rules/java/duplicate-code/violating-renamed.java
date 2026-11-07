class Copies {
  int compute(int a, int b) {
    int x = a + b * 2 - a / 3 + b % 4 - a * b + 7;
    int y = x * x - a + b / 2 + x % 5 - 9;
    int z = y + x - a * 3 + b / 4 - x * 2 + 1;
    int w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5;
    int v = w - z + y * 2 - x + a / 5 + b * 4 - 6;
    int u = v * 2 - w + z / 3 - y + x % 4 + a - b;
    int s = a + b * 2 - a / 3 + b % 4 - a * b + 7;
    int t = s * s - a + b / 2 + s % 5 - 9;
    int r = t + s - a * 3 + b / 4 - s * 2 + 1;
    int q = r * 2 + t - s / 4 + a * 3 - b % 2 + 5;
    int p = q - r + t * 2 - s + a / 5 + b * 4 - 6;
    int o = p * 2 - q + r / 3 - t + s % 4 + a - b;
    return x + y + z + w + v + u;
  }
}
