import javax.crypto.Cipher;

class Modes {
  Cipher explicit() throws Exception {
    return Cipher.getInstance("AES/ECB/PKCS5Padding");
  }

  Cipher implicit() throws Exception {
    return Cipher.getInstance("AES");
  }
}
