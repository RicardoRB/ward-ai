final class Shift {
  int wide(int flags) {
    return (flags << 2) + 1;
  }

  int grow(int flags) {
    flags <<= 2 + 1;
    return flags;
  }
}

final class Hex {
  private static final char[] DIGITS = {'0', '1'};

  String code(char ch) {
    return "\\u" + DIGITS[ch >> 12 & 15] + DIGITS[ch >> 8 & 15];
  }
}
