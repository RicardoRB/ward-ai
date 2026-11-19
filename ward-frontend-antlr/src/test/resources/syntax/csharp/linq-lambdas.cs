using System;
using System.Collections.Generic;
using System.Linq;

namespace Demo.Query;

public static class Queries
{
    public static void Run(List<int> numbers, Dictionary<string, int> map)
    {
        var evens = from n in numbers
                    where n % 2 == 0
                    orderby n descending
                    select n * 2;
        var groups = from n in numbers
                     group n by n % 3 into g
                     let total = g.Sum()
                     select new { g.Key, total };
        var joined = from a in numbers
                     join b in numbers on a equals b
                     select (a, b);

        Func<int, int> twice = x => x * 2;
        Func<int, int, int> add = static (a, b) => a + b;
        Func<int, int> typed = (int x) => x + 1;
        Action<string> log = async message => await Task.Delay(1);
        var withDefault = (int x = 5) => x;
        var attributed = [Obsolete] (int x) => x;

        var total = numbers.Where(n => n > 1).Select((n, i) => n * i).Sum();
        var (key, value) = map.First();
        (int a, int b) tuple = (1, 2);
        (a, b) = (b, a);
        var text = $"total: {total,5:N2} and {(total > 1 ? "big" : "small")}";
        var verbatim = @"C:\path\file";
        var literal = "bytes"u8;
        var raw = """
            {"name": "ward", "quote": "he said \"hi\""}
            """;
        var interpolatedRaw = $"""value {total}""";
        var index = ^1;
        var range = numbers[1..^1];
        var obj = new Dictionary<string, int> { ["a"] = 1, ["b"] = 2 };
        var anon = new { Name = "x", Count = 1 };
        int? maybe = null;
        maybe ??= 3;
        var cast = (double)total / (int)2.5;
        var typeName = nameof(Queries);
        var size = typeof(List<>).Name;
        Console.WriteLine(evens.Count() + groups.Count() + joined.Count() + cast + typeName.Length + size.Length);
    }
}
