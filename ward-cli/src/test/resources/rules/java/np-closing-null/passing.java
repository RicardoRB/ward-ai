class Closing {
  void run(java.io.Reader other) throws Exception {
    java.io.Reader reader = null;
    if (reader != null) {
      reader.close();
    }
    other.close();
  }
}
