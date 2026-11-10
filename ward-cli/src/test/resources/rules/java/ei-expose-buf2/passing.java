final class T {
  private ByteBuffer buf;

  T(byte[] data) {
    this.buf = ByteBuffer.wrap(data.clone());
  }
}
