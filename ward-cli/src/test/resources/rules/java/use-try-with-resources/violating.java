import java.io.InputStream;

final class Closer {
  void drain(InputStream in) throws Exception {
    try {
      in.read();
    } finally {
      in.close();
    }
  }
}
