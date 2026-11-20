using System.Security.Cryptography;

public class Ciphers
{
    public Aes Strong()
    {
        var aes = Aes.Create();
        aes.Mode = CipherMode.CBC;
        return aes;
    }

    public AesGcm Authenticated(byte[] key)
    {
        return new AesGcm(key, 16);
    }
}
