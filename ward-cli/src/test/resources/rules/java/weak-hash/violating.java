import java.security.MessageDigest;
import org.apache.commons.codec.digest.DigestUtils;

class Hashes {
  byte[] md5(byte[] data) throws Exception {
    return MessageDigest.getInstance("MD5").digest(data);
  }

  byte[] sha1(byte[] data) throws Exception {
    return MessageDigest.getInstance("SHA-1", "SUN").digest(data);
  }

  String codec(String text) {
    return DigestUtils.md5Hex(text);
  }
}
