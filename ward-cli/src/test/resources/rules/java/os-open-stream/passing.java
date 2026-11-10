import java.io.ByteArrayInputStream;
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

  void memory(byte[] data) throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    in.read();
  }
}

