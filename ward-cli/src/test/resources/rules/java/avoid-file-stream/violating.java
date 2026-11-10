import java.io.FileInputStream;
class Files1 {
  Object open(String name) throws Exception {
    return new FileInputStream(name);
  }
}
