class Reader {
  void read(java.io.BufferedReader reader) throws java.io.IOException {
    String line = reader.readLine();
    while (line != null) {
      line = reader.readLine();
    }
  }
}
