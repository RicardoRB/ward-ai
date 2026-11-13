import javax.crypto.spec.GCMParameterSpec;

class Nonces {
  GCMParameterSpec literal() {
    return new GCMParameterSpec(128, "nonce-nonce".getBytes());
  }

  GCMParameterSpec zeros() {
    return new GCMParameterSpec(128, new byte[12]);
  }
}
