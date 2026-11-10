final class T {
  private static ByteBuffer buf;

  static void set(byte[] data) {
    buf = ByteBuffer.wrap(data.clone());
  }
}
