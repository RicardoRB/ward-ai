import java.io.FileInputStream;

class Sample {
  void quick(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    in.close();
  }

  void guarded(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    try {
      in.read();
    } finally {
      in.close();
    }
  }

  void open(java.io.File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    in.read();
  }
}

