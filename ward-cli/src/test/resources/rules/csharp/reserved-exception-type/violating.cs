public class Service
{
    public void Run()
    {
        throw new Exception("failed");
    }

    public void Stop()
    {
        throw new System.ApplicationException();
    }
}
