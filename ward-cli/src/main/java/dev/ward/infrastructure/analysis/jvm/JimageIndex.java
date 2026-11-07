package dev.ward.infrastructure.analysis.jvm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * The index of a JDK {@code lib/modules} image. A perfect hash maps a resource name to a location,
 * which holds the offset and size of the resource; names are rebuilt from the string table to check
 * a hit.
 */
final class JimageIndex {
  private static final int OFFSET = 5;
  private static final int COMPRESSED = 6;
  private static final int UNCOMPRESSED = 7;
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

  /**
   * Where a resource is.
   *
   * @param offset where its content starts, after the index
   * @param compressed whether it is stored compressed
   * @param size its size once decompressed
   */
  record Location(int offset, boolean compressed, int size) {}

  /** A location with the string-table offsets of the parts of its name. */
  private record Entry(List<Integer> names, Location location) {}

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
  Optional<Location> locate(final String name) {
    if (tableLength == 0) {
      return Optional.empty();
    }
    final int slot = hash(name, HASH_MULTIPLIER) % tableLength;
    final int redirect = image.getInt(redirectStart + slot * Integer.BYTES);
    if (redirect == 0) {
      return Optional.empty();
    }
    final int index = redirect < 0 ? -1 - redirect : hash(name, redirect) % tableLength;
    final Entry entry = entry(image.getInt(offsetsStart + index * Integer.BYTES));
    return fullName(entry).equals(name) ? Optional.of(entry.location()) : Optional.empty();
  }

  /** Reads the content of a resource that is stored as it is. */
  byte[] content(final Location location) {
    final byte[] bytes = new byte[location.size()];
    image.get(indexSize + location.offset(), bytes);
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

  /** Reads the integer at {@code position} in the content of a resource. */
  int integerAt(final Location location, final int position) {
    return image.getInt(indexSize + location.offset() + position);
  }

  private static int hash(final String text, final int seed) {
    int value = seed;
    for (final byte next : text.getBytes(StandardCharsets.UTF_8)) {
      value = value * HASH_MULTIPLIER ^ next & BYTE_MASK;
    }
    return value & POSITIVE_MASK;
  }

  private Entry entry(final int offset) {
    final long[] attributes = new long[ATTRIBUTES];
    int at = locationsStart + offset;
    int data = image.get(at) & BYTE_MASK;
    while (data >>> KIND_SHIFT != ATTRIBUTE_END) {
      final int length = (data & LENGTH_MASK) + 1;
      attributes[data >>> KIND_SHIFT] = bigEndian(at + 1, length);
      at += 1 + length;
      data = image.get(at) & BYTE_MASK;
    }
    return new Entry(
        List.of(
            (int) attributes[MODULE],
            (int) attributes[PARENT],
            (int) attributes[BASE],
            (int) attributes[EXTENSION]),
        new Location(
            (int) attributes[OFFSET], attributes[COMPRESSED] != 0, (int) attributes[UNCOMPRESSED]));
  }

  private long bigEndian(final int position, final int length) {
    long value = 0;
    for (int index = 0; index < length; index++) {
      value = value << Byte.SIZE | image.get(position + index) & BYTE_MASK;
    }
    return value;
  }

  private String fullName(final Entry entry) {
    final StringBuilder name = new StringBuilder();
    final String module = string(entry.names().get(0));
    if (!module.isEmpty()) {
      name.append('/').append(module).append('/');
    }
    final String parent = string(entry.names().get(1));
    if (!parent.isEmpty()) {
      name.append(parent).append('/');
    }
    name.append(string(entry.names().get(2)));
    final String extension = string(entry.names().get(3));
    if (!extension.isEmpty()) {
      name.append('.').append(extension);
    }
    return name.toString();
  }
}
