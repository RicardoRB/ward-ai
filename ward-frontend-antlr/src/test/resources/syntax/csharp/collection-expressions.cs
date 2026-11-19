using System;
using System.Collections.Generic;

namespace Demo.Collections;

public class Lists
{
    private static readonly int[] Numbers = [1, 2, 3];
    private static readonly List<string> Empty = [];

    public static int[] Combine(int[] first, int[] second) => [.. first, 0, .. second];

    public static void Use()
    {
        Span<int> span = [4, 5, 6];
        IEnumerable<int> items = [1, .. Numbers];
        int[][] grid = [[1, 2], [3, 4]];
        var count = Numbers.Length;
        Console.WriteLine(span.Length + items.Count() + grid.Length + count);
    }
}
