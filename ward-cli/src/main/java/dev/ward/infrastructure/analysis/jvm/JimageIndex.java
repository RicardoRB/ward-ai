package dev.ward.infrastructure.analysis.jvm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The index of a JDK {@code lib/modules} image. A perfect hash maps a resource name to a location,
 * which holds the offset and size of the resource; names are rebuilt from the string table to check
 * a hit.
 */
final class JimageIndex {
  /** Position of the offset of a resource, in the attributes of a location. */
  static final int OFFSET = 5;

  /** Position of the compressed size, which is zero for a resource that is stored as it is. */
  static final int COMPRESSED = 6;

  /** Position of the size of a resource after decompression. */
  static final int UNCOMPRESSED = 7;

  private static final int HEADER_SIZE = 28;
  private static final int TABLE_LENGTH_AT = 16;
  private static final int LOCATIONS_SIZE_AT = 20;
  private static final int STRINGS_SIZE_AT = 24;
  private static final int HASH_MULTIPLIER = 0x01000193;
  private static final int POSITIVE_MASK = 0x7FFFFFFF;
  private static final int ATTRIBUTE_END = 0;
  private static final int MODULE = 1;
  private static final int PARENT = 2;
  private static final int BASE = 3;
  private static final int EXTENSION = 4;
  private static final int ATTRIBUTES = 8;
  private static final int KIND_SHIFT = 3;
  private static final int LENGTH_MASK = 7;
  private static final int BYTE_MASK = 0xFF;

  private final ByteBuffer image;
  private final int tableLength;
  private final int redirectStart;
  private final int offsetsStart;
  private final int locationsStart;
  private final int stringsStart;
  private final int indexSize;

  JimageIndex(final ByteBuffer image) {
    this.image = image;
    this.tableLength = image.getInt(TABLE_LENGTH_AT);
    final int locationsSize = image.getInt(LOCATIONS_SIZE_AT);
    final int stringsSize = image.getInt(STRINGS_SIZE_AT);
    this.redirectStart = HEADER_SIZE;
    this.offsetsStart = redirectStart + tableLength * Integer.BYTES;
    this.locationsStart = offsetsStart + tableLength * Integer.BYTES;
    this.stringsStart = locationsStart + locationsSize;
    this.indexSize = stringsStart + stringsSize;
  }

  /**
   * Returns the attributes of the resource {@code name}, such as {@code
   * /java.base/java/lang/Object.class}.
   */
  Optional<long[]> locate(final String name) {
    if (tableLength == 0) {
      return Optional.empty();
    }
    final int slot = hash(name, HASH_MULTIPLIER) % tableLength;
    final int redirect = image.getInt(redirectStart + slot * Integer.BYTES);
    if (redirect == 0) {
      return Optional.empty();
    }
    final int index = redirect < 0 ? -1 - redirect : hash(name, redirect) % tableLength;
    final long[] attributes = attributes(image.getInt(offsetsStart + index * Integer.BYTES));
    return fullName(attributes).equals(name) ? Optional.of(attributes) : Optional.empty();
  }

  /** Reads the content of a resource that is stored as it is. */
  byte[] content(final long[] attributes) {
    final byte[] bytes = new byte[(int) attributes[UNCOMPRESSED]];
    image.get(indexSize + (int) attributes[OFFSET], bytes);
    return bytes;
  }

  /** Reads the string at {@code offset} of the string table. */
  String string(final int offset) {
    final int start = stringsStart + offset;
    int end = start;
    while (image.get(end) != 0) {
      end += 1;
    }
    final byte[] bytes = new byte[end - start];
    image.get(start, bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Returns the offset into the image of the integers of a resource. */
  int integerAt(final long[] attributes, final int position) {
    return image.getInt(indexSize + (int) attributes[OFFSET] + position);
  }

  private static int hash(final String text, final int seed) {
    int value = seed;
    for (final byte next : text.getBytes(StandardCharsets.UTF_8)) {
      value = value * HASH_MULTIPLIER ^ next & BYTE_MASK;
    }
    return value & POSITIVE_MASK;
  }

  private long[] attributes(final int offset) {
    final long[] attributes = new long[ATTRIBUTES];
    int at = locationsStart + offset;
    int data = image.get(at) & BYTE_MASK;
    while (data >>> KIND_SHIFT != ATTRIBUTE_END) {
      final int length = (data & LENGTH_MASK) + 1;
      attributes[data >>> KIND_SHIFT] = bigEndian(at + 1, length);
      at += 1 + length;
      data = image.get(at) & BYTE_MASK;
    }
    return attributes;
  }

  private long bigEndian(final int position, final int length) {
    long value = 0;
    for (int index = 0; index < length; index++) {
      value = value << Byte.SIZE | image.get(position + index) & BYTE_MASK;
    }
    return value;
  }

  private String fullName(final long[] attributes) {
    final StringBuilder name = new StringBuilder();
    final String module = string((int) attributes[MODULE]);
    if (!module.isEmpty()) {
      name.append('/').append(module).append('/');
    }
    final String parent = string((int) attributes[PARENT]);
    if (!parent.isEmpty()) {
      name.append(parent).append('/');
    }
    name.append(string((int) attributes[BASE]));
    final String extension = string((int) attributes[EXTENSION]);
    if (!extension.isEmpty()) {
      name.append('.').append(extension);
    }
    return name.toString();
  }
}
