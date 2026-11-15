import java.io.IOException;

final class Writer {
  void write() throws IOException {
    try {
      flush();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  void flush() throws IOException {}
}

final class Reader {
  void read() {
    try {
      parse();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("context", exception);
    }
  }
  void parse() {}
}
