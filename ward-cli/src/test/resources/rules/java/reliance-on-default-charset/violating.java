import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

final class Encodings {
  byte[] encode(String text) {
    return text.getBytes();
  }

  Object reader(InputStream input) throws Exception {
    return new InputStreamReader(new GZIPInputStream(input));
  }
}
