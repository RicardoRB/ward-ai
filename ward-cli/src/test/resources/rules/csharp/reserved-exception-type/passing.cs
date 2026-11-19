public class Service
{
    public void Run()
    {
        throw new InvalidOperationException("failed");
    }

    public void Stop()
    {
        throw new CustomException();
    }
}
