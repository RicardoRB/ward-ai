class Reader {
  void read(java.io.BufferedReader reader) throws java.io.IOException {
    String line = reader.readLine();
    while (line != null) {
      line = reader.readLine();
    }
  }
  boolean quoted(String text) {
    if (text.startsWith("empty=") || text.indexOf('=') > 0) {
      return true;
    }
    return false;
  }
}
