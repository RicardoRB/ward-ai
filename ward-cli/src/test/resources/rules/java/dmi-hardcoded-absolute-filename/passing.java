final class Check {
  Object open(String directory) {
    return new java.io.File(directory, "app.conf");
  }
}
