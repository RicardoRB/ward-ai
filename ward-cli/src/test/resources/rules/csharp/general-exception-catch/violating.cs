public class Service
{
    public void Run()
    {
        try
        {
            Work();
        }
        catch (Exception ex)
        {
            Log(ex);
        }

        try
        {
            Work();
        }
        catch
        {
        }
    }
}
