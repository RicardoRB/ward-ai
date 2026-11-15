import java.io.InputStream;

final class Closer {
  void drain(InputStream in) throws Exception {
    try (InputStream stream = in) {
      stream.read();
    }
  }
  void hold(Holder holder) {
    try {
      holder.hashCode();
    } finally {
      holder.close();
    }
  }
  static final class Holder {
    void close() {
    }
  }
}
