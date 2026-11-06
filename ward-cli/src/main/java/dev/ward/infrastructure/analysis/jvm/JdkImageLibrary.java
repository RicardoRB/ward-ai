package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.types.ClassFileException;
import dev.ward.analysis.domain.jvm.types.ClassFileReader;
import dev.ward.analysis.domain.jvm.types.JvmClass;
import dev.ward.analysis.domain.jvm.types.JvmLibrary;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The classes of a JDK's {@code lib/modules} image, read with no JDK process and no class loader.
 * The image is its own index: a perfect hash maps a resource name to its location, so a class is
 * found with a few reads of the memory-mapped file. A compressed image, which {@code jlink
 * --compress} makes, is not read, so its classes are unknown.
 */
public final class JdkImageLibrary implements JvmLibrary, java.io.Closeable {
  private static final Logger LOGGER = Logger.getLogger(JdkImageLibrary.class.getName());
  private static final int MAGIC = 0xCAFEDADA;
  private static final int HEADER_SIZE = 28;
  private static final int HASH_MULTIPLIER = 0x01000193;
  private static final int POSITIVE_MASK = 0x7FFFFFFF;
  private static final int ATTRIBUTE_END = 0;
  private static final int MODULE = 1;
  private static final int PARENT = 2;
  private static final int BASE = 3;
  private static final int EXTENSION = 4;
  private static final int OFFSET = 5;
  private static final int COMPRESSED = 6;
  private static final int UNCOMPRESSED = 7;
  private static final int ATTRIBUTES = 8;
  private static final int KIND_SHIFT = 3;
  private static final int LENGTH_MASK = 7;
  private static final int BYTE_MASK = 0xFF;
  private static final Pattern BINARY_NAME = Pattern.compile("[\\p{L}\\p{N}_$]+(\\.[\\p{L}\\p{N}_$]+)*");

  private final FileChannel channel;
  private final ByteBuffer image;
  private final int tableLength;
  private final int redirectStart;
  private final int offsetsStart;
  private final int locationsStart;
  private final int stringsStart;
  private final int indexSize;
  private final ConcurrentMap<String, Optional<String>> modules = new ConcurrentHashMap<>();

  private JdkImageLibrary(final FileChannel channel, final ByteBuffer image) {
    this.channel = channel;
    this.image = image;
    this.tableLength = image.getInt(HEADER_SIZE - 3 * Integer.BYTES);
    final int locationsSize = image.getInt(HEADER_SIZE - 2 * Integer.BYTES);
    final int stringsSize = image.getInt(HEADER_SIZE - Integer.BYTES);
    this.redirectStart = HEADER_SIZE;
    this.offsetsStart = redirectStart + tableLength * Integer.BYTES;
    this.locationsStart = offsetsStart + tableLength * Integer.BYTES;
    this.stringsStart = locationsStart + locationsSize;
    this.indexSize = stringsStart + stringsSize;
  }

  /**
   * Opens the image {@code modules} of a JDK, normally {@code <java.home>/lib/modules}.
   *
   * @param modules the image file
   * @return the library, empty when the file is missing, is not a version 1 image or is compressed
   */
  public static Optional<JdkImageLibrary> open(final Path modules) {
    FileChannel channel = null;
    try {
      channel = FileChannel.open(modules, StandardOpenOption.READ);
      final ByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
      mapped.order(ByteOrder.LITTLE_ENDIAN);
      if (mapped.getInt(0) != MAGIC) {
        mapped.order(ByteOrder.BIG_ENDIAN);
      }
      if (mapped.getInt(0) != MAGIC || mapped.getInt(Integer.BYTES) >>> 16 != 1) {
        channel.close();
        return Optional.empty();
      }
      return Optional.of(new JdkImageLibrary(channel, mapped));
    } catch (IOException | IllegalArgumentException unreadable) {
      LOGGER.log(Level.FINE, "event=jdk-image-unreadable");
      closeQuietly(channel);
      return Optional.empty();
    }
  }

  private static void closeQuietly(final FileChannel channel) {
    if (channel != null) {
      try {
        channel.close();
      } catch (IOException ignored) {
        LOGGER.log(Level.FINE, "event=jdk-image-close-failed");
      }
    }
  }

  @Override
  public boolean exists(final String name) {
    return location(resourceName(name)).isPresent();
  }

  @Override
  public Optional<JvmClass> find(final String name) {
    final Optional<long[]> found = location(resourceName(name));
    if (found.isEmpty() || found.get()[COMPRESSED] != 0) {
      return Optional.empty();
    }
    final long[] attributes = found.get();
    final int size = (int) attributes[UNCOMPRESSED];
    final byte[] bytes = new byte[size];
    image.get(indexSize + (int) attributes[OFFSET], bytes);
    try {
      return Optional.of(ClassFileReader.read(bytes));
    } catch (ClassFileException unreadable) {
      LOGGER.log(Level.FINE, "event=jdk-class-unreadable");
      return Optional.empty();
    }
  }

  /** The image name {@code /java.base/java/lang/Object.class} of a binary name, or empty. */
  private Optional<String> resourceName(final String name) {
    if (!BINARY_NAME.matcher(name).matches()) {
      return Optional.empty();
    }
    final int lastDot = name.lastIndexOf('.');
    final String packageName = lastDot < 0 ? "" : name.substring(0, lastDot);
    return module(packageName).map(module -> "/" + module + "/" + name.replace('.', '/') + ".class");
  }

  private Optional<long[]> location(final Optional<String> resource) {
    return resource.flatMap(this::locate);
  }

  private Optional<String> module(final String packageName) {
    return modules.computeIfAbsent(packageName, this::moduleOf);
  }

  /** Reads {@code /packages/<package>}: pairs of (isEmpty, module name offset) in image order. */
  private Optional<String> moduleOf(final String packageName) {
    if (packageName.isEmpty()) {
      return Optional.empty();
    }
    final Optional<long[]> found = locate("/packages/" + packageName);
    if (found.isEmpty() || found.get()[COMPRESSED] != 0) {
      return Optional.empty();
    }
    final int start = indexSize + (int) found.get()[OFFSET];
    final int size = (int) found.get()[UNCOMPRESSED];
    for (int at = start; at + 2 * Integer.BYTES <= start + size; at += 2 * Integer.BYTES) {
      if (image.getInt(at) == 0) {
        return Optional.of(string(image.getInt(at + Integer.BYTES)));
      }
    }
    return Optional.empty();
  }

  private Optional<long[]> locate(final String name) {
    if (tableLength == 0) {
      return Optional.empty();
    }
    final int slot = hash(name, HASH_MULTIPLIER) % tableLength;
    final int redirect = image.getInt(redirectStart + slot * Integer.BYTES);
    final int index;
    if (redirect < 0) {
      index = -1 - redirect;
    } else if (redirect > 0) {
      index = hash(name, redirect) % tableLength;
    } else {
      return Optional.empty();
    }
    final long[] attributes = attributes(image.getInt(offsetsStart + index * Integer.BYTES));
    return fullName(attributes).equals(name) ? Optional.of(attributes) : Optional.empty();
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
    while (true) {
      final int data = image.get(at++) & BYTE_MASK;
      final int kind = data >>> KIND_SHIFT;
      if (kind == ATTRIBUTE_END) {
        return attributes;
      }
      long value = 0;
      for (int length = (data & LENGTH_MASK) + 1; length > 0; length--) {
        value = value << Byte.SIZE | image.get(at++) & BYTE_MASK;
      }
      attributes[kind] = value;
    }
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

  private String string(final int offset) {
    int end = stringsStart + offset;
    while (image.get(end) != 0) {
      end++;
    }
    final byte[] bytes = new byte[end - stringsStart - offset];
    image.get(stringsStart + offset, bytes);
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
