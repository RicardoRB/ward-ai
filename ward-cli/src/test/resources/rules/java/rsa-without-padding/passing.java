import javax.crypto.Cipher;

class Rsa {
  Cipher oaep() throws Exception {
    return Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
  }
}
