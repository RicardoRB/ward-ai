using System;
using System.Runtime.InteropServices;

namespace Demo.Misc;

public unsafe struct Buffer
{
    public fixed byte Data[16];

    public static void Fill(byte* pointer, int count)
    {
        for (var i = 0; i < count; i++)
        {
            *(pointer + i) = (byte)i;
        }
        fixed (byte* p = new byte[4])
        {
            *p = 1;
        }
    }
}

public ref struct Cursor
{
    private ref readonly int _value;
    public ref int Next(scoped ref int position) => ref position;
}

public static class Interop
{
    [DllImport("lib", EntryPoint = "call")]
    public static extern int Call(int value);

    public static void Work(object gate, ref int counter, out int result, in int input)
    {
        lock (gate)
        {
            counter++;
        }
        checked
        {
            result = counter + input;
        }
        try
        {
            throw new InvalidOperationException("x");
        }
        catch (InvalidOperationException ex) when (ex.Message.Length > 0)
        {
            result = 0;
        }
        finally
        {
            counter = 0;
        }
        goto done;
    done:
        Console.WriteLine(result);
        int.TryParse("1", out var parsed);
        Console.WriteLine(parsed);
    }
}
