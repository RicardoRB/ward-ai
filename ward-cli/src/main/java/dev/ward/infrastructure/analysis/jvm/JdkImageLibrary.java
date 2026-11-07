package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.types.ClassFileException;
import dev.ward.analysis.domain.jvm.types.ClassFileReader;
import dev.ward.analysis.domain.jvm.types.JvmClass;
import dev.ward.analysis.domain.jvm.types.JvmLibrary;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
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
 * The image is its own index, so a class is found with a few reads of the memory-mapped file. A
 * compressed image, which {@code jlink --compress} makes, is not read, so its classes are unknown.
 */
public final class JdkImageLibrary implements JvmLibrary, Closeable {
  private static final Logger LOGGER = Logger.getLogger(JdkImageLibrary.class.getName());
  private static final int MAGIC = 0xCAFEDADA;
  private static final int VERSION_SHIFT = 16;
  private static final Pattern BINARY_NAME =
      Pattern.compile("[\\p{L}\\p{N}_$]+(\\.[\\p{L}\\p{N}_$]+)*");

  private final FileChannel channel;
  private final JimageIndex index;
  private final ConcurrentMap<String, Optional<String>> modules = new ConcurrentHashMap<>();

  private JdkImageLibrary(final FileChannel channel, final JimageIndex index) {
    this.channel = channel;
    this.index = index;
  }

  /**
   * Opens the image {@code modules} of a JDK, normally {@code <java.home>/lib/modules}.
   *
   * @param modules the image file
   * @return the library, empty when the file is missing or is not a version 1 image
   */
  public static Optional<JdkImageLibrary> open(final Path modules) {
    final FileChannel channel;
    try {
      channel = FileChannel.open(modules, StandardOpenOption.READ);
    } catch (IOException unreadable) {
      LOGGER.log(Level.FINE, "event=jdk-image-unreadable");
      return Optional.empty();
    }
    try {
      final ByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
      mapped.order(ByteOrder.LITTLE_ENDIAN);
      if (mapped.getInt(0) != MAGIC) {
        mapped.order(ByteOrder.BIG_ENDIAN);
      }
      if (mapped.getInt(0) == MAGIC && mapped.getInt(Integer.BYTES) >>> VERSION_SHIFT == 1) {
        return Optional.of(new JdkImageLibrary(channel, new JimageIndex(mapped)));
      }
    } catch (IOException | IndexOutOfBoundsException unreadable) {
      LOGGER.log(Level.FINE, "event=jdk-image-unreadable");
    }
    closeQuietly(channel);
    return Optional.empty();
  }

  private static void closeQuietly(final FileChannel channel) {
    try {
      channel.close();
    } catch (IOException ignored) {
      LOGGER.log(Level.FINE, "event=jdk-image-close-failed");
    }
  }

  @Override
  public boolean exists(final String name) {
    return resourceName(name).flatMap(index::locate).isPresent();
  }

  @Override
  public Optional<JvmClass> find(final String name) {
    final Optional<JimageIndex.Location> found = resourceName(name).flatMap(index::locate);
    if (found.isEmpty() || found.get().compressed()) {
      return Optional.empty();
    }
    try {
      return Optional.of(ClassFileReader.read(index.content(found.get())));
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
    return modules
        .computeIfAbsent(packageName, this::moduleOf)
        .map(module -> "/" + module + "/" + name.replace('.', '/') + ".class");
  }

  /** Reads {@code /packages/<package>}: pairs of (isEmpty, module name offset) in image order. */
  private Optional<String> moduleOf(final String packageName) {
    if (packageName.isEmpty()) {
      return Optional.empty();
    }
    final Optional<JimageIndex.Location> found = index.locate("/packages/" + packageName);
    if (found.isEmpty() || found.get().compressed()) {
      return Optional.empty();
    }
    final int size = found.get().size();
    for (int at = 0; at + 2 * Integer.BYTES <= size; at += 2 * Integer.BYTES) {
      if (index.integerAt(found.get(), at) == 0) {
        return Optional.of(index.string(index.integerAt(found.get(), at + Integer.BYTES)));
      }
    }
    return Optional.empty();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
