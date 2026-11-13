import java.security.MessageDigest;
import org.apache.commons.codec.digest.DigestUtils;

class Hashes {
  byte[] sha256(byte[] data) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(data);
  }

  String codec(String text) {
    return DigestUtils.sha256Hex(text);
  }
}
