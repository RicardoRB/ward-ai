namespace Shop.Models;

public record Money(decimal Amount, string Currency)
{
    public static Money Zero => new(0m, "EUR");

    public Money Add(Money other) => this with { Amount = Amount + other.Amount };
}

public sealed record class Customer(string Name, int Age) : Person(Name)
{
    public string? Nickname { get; init; }
}

public readonly record struct Point(int X, int Y);

public record Person(string Name);

public abstract record Shape
{
    public abstract double Area { get; }
}
