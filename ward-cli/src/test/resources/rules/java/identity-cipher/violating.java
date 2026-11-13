import javax.crypto.Cipher;
import javax.crypto.NullCipher;

class Wrap {
  Cipher plain() {
    return new NullCipher();
  }

  Cipher qualified() {
    return new javax.crypto.NullCipher();
  }
}
