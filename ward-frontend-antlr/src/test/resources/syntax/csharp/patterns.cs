using System;

namespace Demo.Patterns;

public static class Classifier
{
    public static string Describe(object value) => value switch
    {
        null => "nothing",
        int n when n < 0 => "negative",
        int and > 100 and < 1000 => "large",
        int or long => "integer",
        string { Length: 0 } => "empty",
        string { Length: > 10 } s => $"long {s}",
        (1, var y) => "pair " + y,
        [1, 2, ..] => "starts with one two",
        [.., 9] => "ends with nine",
        [var first, .., var last] => $"{first}..{last}",
        not null => "something",
    };

    public static bool Check(object o) => o is not null and not string;

    public static int Sum(Shape shape)
    {
        if (shape is Circle { Radius: var r } c && r > 0)
        {
            return (int)(c.Radius * 2);
        }
        switch (shape)
        {
            case Square sq when sq.Side > 3:
                return 1;
            case Rect(var w, var h):
                return w * h;
            default:
                return 0;
        }
    }
}
