import java.io.InputStream;

final class Check {
  void test(InputStream in) throws Exception {
    in.skip(10);
  }
}
