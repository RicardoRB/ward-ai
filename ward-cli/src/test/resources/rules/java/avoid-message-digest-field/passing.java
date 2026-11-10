import java.security.MessageDigest;

final class Digests {
  byte[] hash(byte[] input) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return digest.digest(input);
  }
}
