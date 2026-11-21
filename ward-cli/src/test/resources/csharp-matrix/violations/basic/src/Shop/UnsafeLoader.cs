using System.Runtime.Serialization.Formatters.Binary;

namespace Shop;

public class UnsafeLoader
{
    public object? Read(Stream input)
    {
        var formatter = new BinaryFormatter();
        return formatter.Deserialize(input);
    }
}
