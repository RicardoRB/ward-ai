import javax.crypto.spec.SecretKeySpec;
class Keys {
  SecretKeySpec a() {
    return new SecretKeySpec("secret-key-16bit".getBytes(), "AES");
  }
}
