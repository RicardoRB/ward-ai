final class T {
  private final byte[] data = new byte[1];

  ByteBuffer view() {
    return ByteBuffer.wrap(data).asReadOnlyBuffer();
  }

  ByteBuffer other(byte[] data) {
    return ByteBuffer.wrap(data.clone());
  }
}
