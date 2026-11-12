import javax.crypto.Cipher;

class Rsa {
  Cipher raw() throws Exception {
    return Cipher.getInstance("RSA/ECB/NoPadding");
  }
}
