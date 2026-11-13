import javax.crypto.Cipher;

class Modes {
  Cipher gcm() throws Exception {
    return Cipher.getInstance("AES/GCM/NoPadding");
  }

  Cipher cbc() throws Exception {
    return Cipher.getInstance("AES/CBC/PKCS5Padding");
  }
}
