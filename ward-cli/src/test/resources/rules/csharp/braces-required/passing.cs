public class Flow
{
    public void Run(int value)
    {
        if (value > 0)
        {
            Work();
        }
        else if (value < 0)
        {
            Rest();
        }
        while (value > 0)
        {
            value--;
        }
    }
}
