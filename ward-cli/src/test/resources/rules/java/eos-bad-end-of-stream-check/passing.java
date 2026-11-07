final class Reader {
  void drain(java.io.InputStream in) throws java.io.IOException {
    int c;
    while ((c = in.read()) != -1) {
      continue;
    }
  }
}
