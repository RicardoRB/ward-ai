using System.Net;
using System.Net.Http;

public class Clients
{
    public void Global()
    {
        ServicePointManager.ServerCertificateValidationCallback += (sender, certificate, chain, errors) => true;
    }

    public HttpClient Handler()
    {
        var handler = new HttpClientHandler();
        handler.ServerCertificateCustomValidationCallback = (message, certificate, chain, errors) => true;
        return new HttpClient(handler);
    }

    public HttpClient Shortcut()
    {
        var handler = new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback = HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
        };
        return new HttpClient(handler);
    }
}
