final class Flags {
  boolean one(int flags) {
    return (flags & 3) == 3;
  }

  boolean two(int flags) {
    return (flags & 0x0F) == 0x05;
  }
}
