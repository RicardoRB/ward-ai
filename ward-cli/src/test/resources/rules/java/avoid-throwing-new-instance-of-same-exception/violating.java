import java.io.IOException;

final class Writer {
  void write() throws IOException {
    try {
      flush();
    } catch (IOException e) {
      throw new IOException(e);
    }
  }

  void flush() throws IOException {}
}
