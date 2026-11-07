final class Hashes {
  String[] parts(String path) {
    return path.split(Pattern.quote(File.separator));
  }
}
