import javax.crypto.Cipher;

class Modes {
  Cipher pkcs5() throws Exception {
    return Cipher.getInstance("AES/CBC/PKCS5Padding");
  }

  Cipher pkcs7() throws Exception {
    return Cipher.getInstance("AES/cbc/PKCS7Padding");
  }
}
