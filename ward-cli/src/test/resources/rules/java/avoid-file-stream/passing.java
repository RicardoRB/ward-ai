import java.nio.file.Files;
class Files1 {
  Object open(String name) throws Exception {
    return Files.newInputStream(java.nio.file.Path.of(name));
  }
}
