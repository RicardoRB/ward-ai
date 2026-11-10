final class Flags {
  boolean high(int flags) {
    return (flags & 0x80) < 0;
  }

  boolean top(int flags) {
    return (flags & 0x80000000) >= 0;
  }
}
