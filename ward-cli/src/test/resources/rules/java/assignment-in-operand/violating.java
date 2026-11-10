class Reader {
  void read(java.io.BufferedReader reader) throws java.io.IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      System.out.println(line);
    }
  }
}
