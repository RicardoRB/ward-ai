import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

class Ciphers {
  Cipher aes() throws Exception {
    return Cipher.getInstance("AES/GCM/NoPadding");
  }

  KeyGenerator key() throws Exception {
    return KeyGenerator.getInstance("AES");
  }
}
