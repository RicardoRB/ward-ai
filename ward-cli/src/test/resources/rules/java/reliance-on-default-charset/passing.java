final class Encodings {
  byte[] encode(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
