import java.security.SecureRandom;
import javax.crypto.spec.SecretKeySpec;

class Keys {
  Object generated() {
    byte[] key = new byte[16];
    new SecureRandom().nextBytes(key);
    return new SecretKeySpec(key, "AES");
  }

  Object environment() {
    return new SecretKeySpec(System.getenv("APP_KEY").getBytes(), "AES");
  }
}
