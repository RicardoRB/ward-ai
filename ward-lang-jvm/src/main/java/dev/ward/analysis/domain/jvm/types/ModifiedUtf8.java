package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Reads the {@code Utf8} entries of a constant pool, which use a modified UTF-8. */
final class ModifiedUtf8 {
  private static final int UNSIGNED_SHORT = 0xFFFF;
  private static final int UNSIGNED_BYTE = 0xFF;
  private static final int ASCII_LIMIT = 0x80;
  private static final int TWO_BYTE_PREFIX = 0b110;
  private static final int PREFIX_SHIFT = 5;
  private static final int LOW_FIVE_BITS = 0x1F;
  private static final int LOW_SIX_BITS = 0x3F;
  private static final int LOW_FOUR_BITS = 0x0F;
  private static final int SIX = 6;
  private static final int TWELVE = 12;
  private static final int THREE_BYTES = 3;

  private ModifiedUtf8() {}

  /** Reads a length-prefixed string from {@code in}. */
  static String read(final ByteBuffer in) {
    final int length = in.getShort() & UNSIGNED_SHORT;
    final byte[] bytes = new byte[length];
    in.get(bytes);
    boolean ascii = true;
    for (final byte value : bytes) {
      ascii &= value > 0;
    }
    return ascii ? new String(bytes, StandardCharsets.US_ASCII) : decode(bytes);
  }

  /**
   * Decodes the modified UTF-8 of class files: two-byte NUL, and surrogates as three bytes each.
   */
  private static String decode(final byte[] bytes) {
    final StringBuilder text = new StringBuilder(bytes.length);
    int at = 0;
    while (at < bytes.length) {
      final int first = bytes[at] & UNSIGNED_BYTE;
      final int next = at + 1 < bytes.length ? bytes[at + 1] & LOW_SIX_BITS : 0;
      if (first < ASCII_LIMIT) {
        text.append((char) first);
        at += 1;
      } else if (first >> PREFIX_SHIFT == TWO_BYTE_PREFIX) {
        text.append((char) ((first & LOW_FIVE_BITS) << SIX | next));
        at += 2;
      } else {
        final int last = at + 2 < bytes.length ? bytes[at + 2] & LOW_SIX_BITS : 0;
        text.append((char) ((first & LOW_FOUR_BITS) << TWELVE | next << SIX | last));
        at += THREE_BYTES;
      }
    }
    return text.toString();
  }
}
