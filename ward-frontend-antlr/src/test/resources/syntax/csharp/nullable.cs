#nullable enable

using System;
using System.Collections.Generic;

namespace Demo.Nullable;

public class Node<T> where T : class?
{
    public T? Value { get; set; }
    public Node<T>? Next { get; set; }
    public string? Label { get; init; }

    public string Describe(string? hint, Dictionary<string, int?>? counts)
    {
        var length = hint?.Length ?? 0;
        var first = counts?["a"] ?? 0;
        ArgumentNullException.ThrowIfNull(Value!);
        Label ??= "none";
        return $"{length}{first}{Label!}";
    }
}

public static class Guard
{
    public static T NotNull<T>(T? value) where T : struct => value ?? default;
    public static bool Try([System.Diagnostics.CodeAnalysis.NotNullWhen(true)] string? text) => text is not null;
}
