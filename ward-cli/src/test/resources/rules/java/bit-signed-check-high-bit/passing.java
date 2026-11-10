final class Flags {
  boolean high(int flags) {
    return (flags & 0x80) != 0;
  }

  boolean low(int flags) {
    return (flags & 4) < 0;
  }
}
