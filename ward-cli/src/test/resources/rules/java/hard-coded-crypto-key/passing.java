import javax.crypto.spec.SecretKeySpec;
class Keys {
  SecretKeySpec a(byte[] key) {
    return new SecretKeySpec(key, "AES");
  }
}
