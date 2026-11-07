package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** The constant pool of a class file: the strings, numbers and class names its attributes use. */
final class ConstantPool {
  private static final int UTF8 = 1;
  private static final int INTEGER = 3;
  private static final int FLOAT = 4;
  private static final int LONG = 5;
  private static final int DOUBLE = 6;
  private static final int CLASS = 7;
  private static final int STRING = 8;
  private static final int METHOD_HANDLE = 15;
  private static final int METHOD_TYPE = 16;
  private static final int MODULE = 19;
  private static final int PACKAGE = 20;
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
  /** Tags of four-byte entries: field, method, interface method, name and type, dynamic. */
  private static final int[] FOUR_BYTE_TAGS = {9, 10, 11, 12, 17, 18};

  private final Object[] values;
  private final int[] indexes;

  private ConstantPool(final Object[] values, final int[] indexes) {
    this.values = values;
    this.indexes = indexes;
  }

  /** Reads the pool that starts at the current position of {@code in}. */
  static ConstantPool read(final ByteBuffer in) throws ClassFileException {
    final int count = in.getShort() & UNSIGNED_SHORT;
    final ConstantPool pool = new ConstantPool(new Object[count], new int[count]);
    int slot = 1;
    while (slot < count) {
      slot += pool.entry(in, slot);
    }
    return pool;
  }

  /** Reads the entry for {@code slot} and returns how many slots it takes. */
  private int entry(final ByteBuffer in, final int slot) throws ClassFileException {
    final int tag = in.get() & UNSIGNED_BYTE;
    switch (tag) {
      case UTF8 -> values[slot] = utf8(in);
      case INTEGER -> values[slot] = (long) in.getInt();
      case FLOAT -> values[slot] = (double) in.getFloat();
      case LONG -> values[slot] = in.getLong();
      case DOUBLE -> values[slot] = in.getDouble();
      case CLASS, STRING, METHOD_TYPE, MODULE, PACKAGE -> reference(in, slot, tag);
      case METHOD_HANDLE -> in.position(in.position() + Byte.BYTES + Short.BYTES);
      default -> skipFourBytes(in, tag);
    }
    return tag == LONG || tag == DOUBLE ? 2 : 1;
  }

  private void reference(final ByteBuffer in, final int slot, final int tag) {
    indexes[slot] = in.getShort() & UNSIGNED_SHORT;
    values[slot] = tag;
  }

  private static void skipFourBytes(final ByteBuffer in, final int tag) throws ClassFileException {
    for (final int known : FOUR_BYTE_TAGS) {
      if (known == tag) {
        in.getInt();
        return;
      }
    }
    throw new ClassFileException("unknown constant pool tag " + tag);
  }

  /** Returns the string at a {@code Utf8} entry. */
  String utf8(final int index) {
    return (String) values[index];
  }

  /** Returns the binary name, with dots, of the {@code Class} entry at {@code index}. */
  String className(final int index) {
    return utf8(indexes[index]).replace('/', '.');
  }

  /**
   * Returns the constant of an {@code Integer}, {@code Float}, {@code Long}, {@code Double} or
   * {@code String} entry.
   */
  Object constant(final int index) {
    final Object value = values[index];
    if (value instanceof Integer tag && tag == STRING) {
      return utf8(indexes[index]);
    }
    return value;
  }

  private static String utf8(final ByteBuffer in) {
    final int length = in.getShort() & UNSIGNED_SHORT;
    final byte[] bytes = new byte[length];
    in.get(bytes);
    boolean ascii = true;
    for (final byte value : bytes) {
      ascii &= value > 0;
    }
    return ascii ? new String(bytes, StandardCharsets.US_ASCII) : modifiedUtf8(bytes);
  }

  /** Decodes the modified UTF-8 of class files: two-byte NUL, and surrogates as three bytes each. */
  private static String modifiedUtf8(final byte[] bytes) {
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
        at += 3;
      }
    }
    return text.toString();
  }
}
