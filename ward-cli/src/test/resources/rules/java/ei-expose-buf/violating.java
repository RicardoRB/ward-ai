final class T {
  private final byte[] data = new byte[1];

  ByteBuffer view() {
    return ByteBuffer.wrap(data);
  }
}
