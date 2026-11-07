function compute(a, b) {
    const x = a + b * 2 - a / 3 + b % 4 - a * b + 7;
    const y = x * x - a + b / 2 + x % 5 - 9;
    const z = y + x - a * 3 + b / 4 - x * 2 + 1;
    const w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5;
    const v = w - z + y * 2 - x + a / 5 + b * 4 - 6;
    const u = v * 2 - w + z / 3 - y + x % 4 + a - b;
    const x = a + b * 2 - a / 3 + b % 4 - a * b + 7;
    const y = x * x - a + b / 2 + x % 5 - 9;
    const z = y + x - a * 3 + b / 4 - x * 2 + 1;
    const w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5;
    const v = w - z + y * 2 - x + a / 5 + b * 4 - 6;
    const u = v * 2 - w + z / 3 - y + x % 4 + a - b;
    return x + y + z + w + v + u;
}
