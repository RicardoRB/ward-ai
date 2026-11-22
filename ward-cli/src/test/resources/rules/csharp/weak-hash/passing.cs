using System.Security.Cryptography;

public class Digests
{
    public byte[] Strong(byte[] data)
    {
        using var sha = SHA256.Create();
        return sha.ComputeHash(data);
    }

    public byte[] Short(byte[] data)
    {
        return SHA512.HashData(data);
    }
}
