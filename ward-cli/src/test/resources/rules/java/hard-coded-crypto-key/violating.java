import javax.crypto.spec.SecretKeySpec;

class Keys {
  Object direct() {
    return new SecretKeySpec("sixteen-byte-aes!".getBytes(), "AES");
  }

  Object variable() throws Exception {
    String material = "sixteen-byte-aes!";
    return new SecretKeySpec(material.getBytes("UTF-8"), "AES");
  }
}
