using Alias = System.Collections.Generic;

public class CountChanged(int count) : Base<int>(count);

public class Wrapper : global::Other.IMessage, ext::Other.IMessage2
{
    ext::Other.IMessage2 ext::Other.IMessage2.Descriptor {
        get { return null; }
    }

    public void Run(int n)
    {
        object?[] values = new object?[n];
        var handler = Results<Ok, Bad<string>> ([FromHeader(Name = key)] string value, int other) => Ok();
        var other = async Task<Results<Ok<Item>, Missing<string>>> (
            int id) => await Load(id);
        var plain = int (int x) => x + 1;
    }
}
