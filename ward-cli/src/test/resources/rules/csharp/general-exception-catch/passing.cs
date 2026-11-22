public class Service
{
    public void Run()
    {
        try
        {
            Work();
        }
        catch (IOException ex)
        {
            Log(ex);
        }

        try
        {
            Work();
        }
        catch (Exception ex)
        {
            Log(ex);
            throw;
        }
    }
}
