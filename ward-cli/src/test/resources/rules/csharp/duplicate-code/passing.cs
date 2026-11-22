class Calculator
{
    int Compute(int a, int b)
    {
        var x = a + b * 2 - a / 3 + b % 4 - a * b + 7;
        var y = x * x - a + b / 2 + x % 5 - 9;
        var z = y + x - a * 3 + b / 4 - x * 2 + 1;
        var w = z * 2 + y - x / 4 + a * 3 - b % 2 + 5;
        var v = w - z + y * 2 - x + a / 5 + b * 4 - 6;
        var u = v * 2 - w + z / 3 - y + x % 4 + a - b;
        return x + y + z + w + v + u;
    }
}
