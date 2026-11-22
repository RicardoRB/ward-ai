class Braces
{
    void Run(int a)
    {
        if (a > 0)
        {
            Go();
        }
        else if (a < 0)
        {
            Stop();
        }
        else
        {
            Wait();
        }
        while (a > 0)
        {
            a--;
        }
        do
        {
            a++;
        } while (a < 5);
        try
        {
            Go();
        }
        catch (Exception e)
        {
            Stop();
        }
        finally
        {
            Wait();
        }
        if (a > 1) { Go(); }
    }
    void Go() { }
    void Stop() { }
    void Wait() { }
}
