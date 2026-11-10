// passes
final class Check {
  void test(OutputStream out) {
    byte[] buf = new byte[10];
    out.write(buf, 2, 8);
  }
}
