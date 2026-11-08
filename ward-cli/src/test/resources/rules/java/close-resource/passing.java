








import java.io.FileInputStream;

class Sample {
  void closed(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    in.read();
    in.close();
  }

  FileInputStream handed(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    return in;
  }

  void resource(java.io.File file) throws Exception {
    try (FileInputStream in = new FileInputStream(file)) {
      in.read();
    }
  }

  void plain() {
    String text = "a";
    text.length();
  }
}
