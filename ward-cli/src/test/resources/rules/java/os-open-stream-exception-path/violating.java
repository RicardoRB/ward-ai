import java.io.FileInputStream;

class Sample {
  void read(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    in.read();
    in.close();
  }
}

