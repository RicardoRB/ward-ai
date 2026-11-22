using System.Security.Cryptography;

public class Ciphers
{
    public SymmetricAlgorithm Old()
    {
        return DES.Create();
    }

    public SymmetricAlgorithm Triple()
    {
        return new TripleDESCryptoServiceProvider();
    }

    public void Pattern(Aes aes)
    {
        aes.Mode = CipherMode.ECB;
    }
}
