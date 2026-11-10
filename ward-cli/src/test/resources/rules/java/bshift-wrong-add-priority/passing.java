final class Shift {
  int wide(int flags) {
    return (flags << 2) + 1;
  }

  int grow(int flags) {
    flags <<= 2 + 1;
    return flags;
  }
}
