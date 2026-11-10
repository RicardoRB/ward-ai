import java.io.InputStream;

final class Check {
  long test(InputStream in) throws Exception {
    return in.skip(10);
  }
}
