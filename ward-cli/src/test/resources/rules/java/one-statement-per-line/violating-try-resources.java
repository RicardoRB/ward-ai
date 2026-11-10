import java.io.StringReader;

class Violating {
  void method() throws Exception {
    try (StringReader a = new StringReader(""); StringReader b = new StringReader("")) {
    }
  }
}
