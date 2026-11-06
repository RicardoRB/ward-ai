import java.io.InputStream;

final class Closer {
  void drain(InputStream in) throws Exception {
    try (InputStream stream = in) {
      stream.read();
    }
  }
}
