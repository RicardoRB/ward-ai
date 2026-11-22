using System.Collections.Generic;

namespace Demo;

public class Service(IRepository repository, string name = "default") : Base(name), IService
{
    private readonly List<string> _log = [];

    public string Name { get; } = name;

    public void Run() => repository.Save(Name);
}

public struct Vector(double x, double y)
{
    public double Length => Math.Sqrt(x * x + y * y);
}

public interface IService
{
    void Run();

    static abstract IService Create();

    string Describe() => "service";
}

internal class Holder<T>(T value) where T : class, new()
{
    public T Value => value;
}
