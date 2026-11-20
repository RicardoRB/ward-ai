import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class Encodings {
  byte[] encode(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  Reader reader(InputStream input) throws java.io.IOException {
    return new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8);
  }
}

interface Upload {
  byte[] getBytes();
}

final class Sizes {
  int size(Upload upload) {
    return upload.getBytes().length;
  }
}
