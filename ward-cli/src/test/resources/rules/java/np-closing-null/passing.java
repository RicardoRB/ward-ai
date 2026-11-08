// passes
final class Check {
  void test(java.io.Closeable given) throws java.io.IOException {
    java.io.Closeable resource = given;
    resource.close();
  }
}
