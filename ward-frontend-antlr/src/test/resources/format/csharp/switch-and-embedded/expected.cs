class Branching
{
    string Name(int a)
    {
        switch (a)
        {
            case 1:
                return "one";
            case 2:
            case 3:
                {
                    return "few";
                }
            default:
                return "many";
        }
    }
    void Loop(int a)
    {
        if (a > 0)
            Go();
        else
            Stop();
        for (var i = 0; i < a; i++)
            Go();
        var s = a switch { 1 => "x", _ => "y" };
    }
    void Go() { }
    void Stop() { }
}
