import java.io.Reader;

class TryWithResources {
  void run(Reader in) throws Exception {
    try (Reader reader = open(in)) {
      read(reader);
    }
    done();
  }
}
