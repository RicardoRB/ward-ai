final class Flags {
  boolean any(int flags) {
    return (flags | 0) == 0 || (flags | 4) == 4;
  }
}
