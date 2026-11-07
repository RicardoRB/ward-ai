final class Log {
  Object open(java.io.File file) throws java.io.IOException {
    return new ObjectOutputStream(new FileOutputStream(file, true));
  }
}
