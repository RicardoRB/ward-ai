using System.Net;
using System.Net.Http;
using System.Net.Security;

public class Clients
{
    public HttpClient Plain()
    {
        return new HttpClient();
    }

    public void Pinned(HttpClientHandler handler, string thumbprint)
    {
        handler.ServerCertificateCustomValidationCallback = (message, certificate, chain, errors) =>
            errors == SslPolicyErrors.None || certificate.Thumbprint == thumbprint;
    }
}
