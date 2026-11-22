using System.Security.Cryptography;

public class Digests
{
    public byte[] Legacy(byte[] data)
    {
        using var md5 = MD5.Create();
        return md5.ComputeHash(data);
    }

    public byte[] Older(byte[] data)
    {
        var provider = new SHA1CryptoServiceProvider();
        return provider.ComputeHash(data);
    }
}
