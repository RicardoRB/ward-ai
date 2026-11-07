package dev.ward.analysis.domain.jvm.types;

import java.nio.ByteBuffer;
import java.util.Set;

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

  private static final Set<Integer> REFERENCE_TAGS =
      Set.of(CLASS, STRING, METHOD_TYPE, MODULE, PACKAGE);

  /** Tags of four-byte entries: field, method, interface method, name and type, dynamic. */
  private static final int[] FOUR_BYTE_TAGS = {9, 10, 11, 12, 17, 18};

  private final Object[] values;
  private final int[] indexes;

  private ConstantPool(final int count) {
    this.values = new Object[count];
    this.indexes = new int[count];
  }

  /** Reads the pool that starts at the current position of {@code in}. */
  static ConstantPool read(final ByteBuffer in) throws ClassFileException {
    final int count = in.getShort() & UNSIGNED_SHORT;
    final ConstantPool pool = new ConstantPool(count);
    int slot = 1;
    while (slot < count) {
      slot += pool.entry(in, slot);
    }
    return pool;
  }

  /** Reads the entry for {@code slot} and returns how many slots it takes. */
  private int entry(final ByteBuffer in, final int slot) throws ClassFileException {
    final int tag = in.get() & UNSIGNED_BYTE;
    values[slot] = value(in, tag);
    if (values[slot] instanceof Integer reference) {
      indexes[slot] = in.getShort() & UNSIGNED_SHORT;
      values[slot] = reference;
    }
    return tag == LONG || tag == DOUBLE ? 2 : 1;
  }

  /** Reads the value of an entry; a reference entry yields its tag, completed by the caller. */
  private static Object value(final ByteBuffer in, final int tag) throws ClassFileException {
    return switch (tag) {
      case UTF8 -> ModifiedUtf8.read(in);
      case INTEGER, FLOAT, LONG, DOUBLE -> number(in, tag);
      case METHOD_HANDLE -> skip(in, Byte.BYTES + Short.BYTES);
      default -> other(in, tag);
    };
  }

  private static Object other(final ByteBuffer in, final int tag) throws ClassFileException {
    return REFERENCE_TAGS.contains(tag) ? (Object) tag : skipFourBytes(in, tag);
  }

  private static Object number(final ByteBuffer in, final int tag) {
    return switch (tag) {
      case INTEGER -> (long) in.getInt();
      case FLOAT -> (double) in.getFloat();
      case LONG -> in.getLong();
      default -> in.getDouble();
    };
  }

  private static Object skip(final ByteBuffer in, final int length) {
    in.position(in.position() + length);
    return null;
  }

  private static Object skipFourBytes(final ByteBuffer in, final int tag)
      throws ClassFileException {
    for (final int known : FOUR_BYTE_TAGS) {
      if (known == tag) {
        return skip(in, Integer.BYTES);
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
}
