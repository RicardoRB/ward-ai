final class Check {
  boolean test(byte b) {
    return (b & 0xff) | 256;
  }
}
