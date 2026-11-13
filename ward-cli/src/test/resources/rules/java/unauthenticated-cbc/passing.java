import javax.crypto.Cipher;

class Modes {
  Cipher gcm() throws Exception {
    return Cipher.getInstance("AES/GCM/NoPadding");
  }

  Cipher ctr() throws Exception {
    return Cipher.getInstance("AES/CTR/NoPadding");
  }
}
