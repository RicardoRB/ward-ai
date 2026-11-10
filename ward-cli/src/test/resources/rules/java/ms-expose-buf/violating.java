final class T {
  private static final byte[] DATA = new byte[1];

  public static ByteBuffer view() {
    return ByteBuffer.wrap(DATA);
  }
}
