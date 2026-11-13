import java.security.SecureRandom;
import javax.crypto.spec.IvParameterSpec;

class Ivs {
  IvParameterSpec random() {
    byte[] iv = new byte[16];
    new SecureRandom().nextBytes(iv);
    return new IvParameterSpec(iv);
  }
}
