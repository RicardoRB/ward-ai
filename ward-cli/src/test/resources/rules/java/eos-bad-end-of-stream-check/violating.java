final class Reader {
  void drain(java.io.InputStream in) throws java.io.IOException {
    while ((byte) in.read() != -1) {
      continue;
    }
  }
}
