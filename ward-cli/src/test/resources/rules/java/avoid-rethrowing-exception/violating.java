import java.io.IOException;

final class Reader {
  void read() throws IOException {
    try {
      load();
    } catch (IOException e) {
      throw e;
    }
  }

  void load() throws IOException {}
}
