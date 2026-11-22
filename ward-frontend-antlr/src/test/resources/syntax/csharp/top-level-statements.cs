using System;
using System.Linq;

var numbers = new[] { 3, 1, 2 };
var sorted = numbers.OrderBy(n => n).ToList();

foreach (var n in sorted)
{
    Console.WriteLine(n);
}

int Twice(int value) => value * 2;

Console.WriteLine(Twice(sorted[0]));

if (args.Length > 0)
{
    return 1;
}

await Task.Delay(10);

return 0;

public record Item(string Name);
