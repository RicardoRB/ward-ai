import javax.crypto.spec.SecretKeySpec;

class Keys {
  Object direct() {
    return new SecretKeySpec("0123456789abcdef".getBytes(), "AES");
  }

  Object variable() throws Exception {
    String key = "0123456789abcdef";
    return new SecretKeySpec(key.getBytes("UTF-8"), "AES");
  }
}
