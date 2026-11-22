using System;
using System.Collections.Generic;

[assembly: System.Reflection.AssemblyVersion("1.0")]

namespace Demo.Attributes;

[AttributeUsage(AttributeTargets.Class | AttributeTargets.Method, AllowMultiple = true)]
public sealed class MarkerAttribute<T> : Attribute
{
    public MarkerAttribute(string name, params int[] values) { }
}

[Marker<int>("a", 1, 2, Name = "x")]
public class Target
{
    [return: System.Diagnostics.CodeAnalysis.MaybeNull]
    public T Convert<T>([Marker<string>("p")] object input) => (T)input;

    public static T Max<T>(T a, T b) where T : IComparable<T> => a.CompareTo(b) > 0 ? a : b;

    public void Params(params ReadOnlySpan<int> values) { }

    public static void Local()
    {
        [Marker<int>("local")]
        static int Inner(int x) => x + 1;
        const int Constant = 5;
        var list = new List<(string Name, int Count)>();
        object boxed = list;
        var any = boxed is List<(string, int)>;
        var generic = Max<int>(Inner(Constant), 2);
        System.Console.WriteLine(any ? generic : -1);
    }
}
