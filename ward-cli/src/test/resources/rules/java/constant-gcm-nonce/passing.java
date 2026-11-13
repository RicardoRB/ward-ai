import java.security.SecureRandom;
import javax.crypto.spec.GCMParameterSpec;

class Nonces {
  GCMParameterSpec fresh() {
    byte[] nonce = new byte[12];
    new SecureRandom().nextBytes(nonce);
    return new GCMParameterSpec(128, nonce);
  }
}
