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
  private static final int FIELD_REF = 9;
  private static final int METHOD_REF = 10;
  private static final int INTERFACE_METHOD_REF = 11;
  private static final int NAME_AND_TYPE = 12;
  private static final int METHOD_HANDLE = 15;
  private static final int METHOD_TYPE = 16;
  private static final int DYNAMIC = 17;
  private static final int INVOKE_DYNAMIC = 18;
  private static final int MODULE = 19;
  private static final int PACKAGE = 20;

  private final Object[] values;
  private final int[] indexes;

  private ConstantPool(final Object[] values, final int[] indexes) {
    this.values = values;
    this.indexes = indexes;
  }

  /** Reads the pool that starts at the current position of {@code in}. */
  static ConstantPool read(final ByteBuffer in) throws ClassFileException {
    final int count = in.getShort() & 0xFFFF;
    final Object[] values = new Object[count];
    final int[] indexes = new int[count];
    for (int slot = 1; slot < count; slot++) {
      final int tag = in.get() & 0xFF;
      switch (tag) {
        case UTF8 -> values[slot] = utf8(in);
        case INTEGER -> values[slot] = (long) in.getInt();
        case FLOAT -> values[slot] = (double) in.getFloat();
        case LONG -> {
          values[slot] = in.getLong();
          slot++;
        }
        case DOUBLE -> {
          values[slot] = in.getDouble();
          slot++;
        }
        case CLASS, STRING, METHOD_TYPE, MODULE, PACKAGE -> {
          indexes[slot] = in.getShort() & 0xFFFF;
          values[slot] = tag;
        }
        case FIELD_REF,
            METHOD_REF,
            INTERFACE_METHOD_REF,
            NAME_AND_TYPE,
            DYNAMIC,
            INVOKE_DYNAMIC -> in.getInt();
        case METHOD_HANDLE -> {
          in.get();
          in.getShort();
        }
        default -> throw new ClassFileException("unknown constant pool tag " + tag);
      }
    }
    return new ConstantPool(values, indexes);
  }

  /** Returns the string at a {@code Utf8} entry. */
  String utf8(final int index) {
    return (String) values[index];
  }

  /** Returns the binary name, with dots, of the {@code Class} entry at {@code index}. */
  String className(final int index) {
    return utf8(indexes[index]).replace('/', '.');
  }

  /** Returns the constant of an {@code Integer}, {@code Float}, {@code Long}, {@code Double} or {@code String} entry. */
  Object constant(final int index) {
    final Object value = values[index];
    if (value instanceof Integer tag && tag == STRING) {
      return utf8(indexes[index]);
    }
    return value;
  }

  private static String utf8(final ByteBuffer in) {
    final int length = in.getShort() & 0xFFFF;
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
      final int first = bytes[at++] & 0xFF;
      if (first < 0x80) {
        text.append((char) first);
      } else if (first >> 5 == 0b110 && at < bytes.length) {
        text.append((char) ((first & 0x1F) << 6 | bytes[at++] & 0x3F));
      } else if (at + 1 < bytes.length) {
        text.append((char) ((first & 0x0F) << 12 | (bytes[at] & 0x3F) << 6 | bytes[at + 1] & 0x3F));
        at += 2;
      }
    }
    return text.toString();
  }
}
