import javax.crypto.Cipher;

class Wrap {
  Cipher aes() throws Exception {
    return Cipher.getInstance("AES/GCM/NoPadding");
  }
}
