#define TRACE_ENABLED
#pragma warning disable CS0168
using System;

namespace Demo.Directives;

public class Conditional
{
#region Logging
    public void Log(string message)
    {
#if DEBUG
        Console.WriteLine("debug " + message);
#elif TRACE_ENABLED && !RELEASE
        Console.WriteLine("trace " + message);
#else
        Console.WriteLine(message);
#endif
    }
#endregion

#if NEVER_DEFINED
    this is not valid C# but the branch is skipped @ #
#endif
}
