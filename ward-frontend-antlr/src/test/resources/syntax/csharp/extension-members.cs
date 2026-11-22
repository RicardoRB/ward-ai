using System;
using System.Collections.Generic;

namespace Demo.Extensions;

public static class Strings
{
    public static bool IsBlank(this string? text) => string.IsNullOrWhiteSpace(text);

    extension(string text)
    {
        public int WordCount => text.Split(' ').Length;

        public string Repeat(int times) => string.Concat(Enumerable.Repeat(text, times));
    }

    extension<T>(IEnumerable<T> source) where T : class
    {
        public bool IsEmpty => !source.Any();
    }
}

public partial class Person
{
    public partial string Name { get; set; }

    public string Title
    {
        get => field ?? "none";
        set => field = value.Trim();
    }

    public void Assign(Person? other, string value)
    {
        other?.Name = value;
        other?.Title ??= value;
    }
}

public class Constraints<T> where T : allows ref struct
{
}

public interface IMath<TSelf> where TSelf : IMath<TSelf>
{
    static abstract TSelf operator +(TSelf left, TSelf right);
    static virtual TSelf operator checked -(TSelf left, TSelf right) => left;
    static abstract bool operator ==(TSelf left, TSelf right);
    static abstract TSelf operator >>>(TSelf left, int shift);
}
