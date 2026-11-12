import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

class Ciphers {
  Cipher des() throws Exception {
    return Cipher.getInstance("DES/CBC/PKCS5Padding");
  }

  Cipher rc4() throws Exception {
    return Cipher.getInstance("RC4");
  }

  KeyGenerator triple() throws Exception {
    return KeyGenerator.getInstance("DESede");
  }
}
