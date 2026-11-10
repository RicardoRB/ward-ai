final class Shift {
  int wide(int flags) {
    return flags << 2 + 1;
  }

  int narrow(int flags, int width) {
    return flags >> width - 1;
  }
}
