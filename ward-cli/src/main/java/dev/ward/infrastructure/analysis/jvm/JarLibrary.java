package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.types.ClassFileException;
import dev.ward.analysis.domain.jvm.types.ClassFileReader;
import dev.ward.analysis.domain.jvm.types.JvmClass;
import dev.ward.analysis.domain.jvm.types.JvmLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The classes of one jar. The class-name index is cached under the SHA-256 of the jar's content, so
 * a warm run knows which classes the jar holds without opening it again; the jar is opened only to
 * read a class that a rule asked about.
 */
public final class JarLibrary implements JvmLibrary {
  private static final Logger LOGGER = Logger.getLogger(JarLibrary.class.getName());
  private static final String CLASS_SUFFIX = ".class";
  private static final String INDEX_PREFIX = "jar-";
  private static final String INDEX_SUFFIX = ".idx";
  private static final String MULTI_RELEASE = "META-INF/";
  private static final String MODULE_INFO = "module-info";
  private static final int BUFFER = 8192;

  private final Path jar;
  private final Set<String> names;

  private JarLibrary(final Path jar, final Set<String> names) {
    this.jar = jar;
    this.names = names;
  }

  /**
   * Opens the jar at {@code jar}.
   *
   * @param jar the jar file
   * @param cacheDirectory where to keep the class-name index, empty for no cache
   * @return the library, empty when the jar cannot be read
   */
  public static Optional<JarLibrary> open(final Path jar, final Optional<Path> cacheDirectory) {
    try {
      final Optional<Path> cached = cacheDirectory.map(directory -> indexFile(jar, directory));
      if (cached.isPresent() && Files.isRegularFile(cached.get())) {
        LOGGER.log(Level.FINE, "event=jar-index-hit");
        return Optional.of(
            new JarLibrary(
                jar, new HashSet<>(Files.readAllLines(cached.get(), StandardCharsets.UTF_8))));
      }
      final Set<String> found = classNames(jar);
      cached.ifPresent(path -> store(path, found));
      return Optional.of(new JarLibrary(jar, found));
    } catch (IOException unreadable) {
      LOGGER.log(Level.WARNING, "event=classpath-entry-unreadable");
      return Optional.empty();
    }
  }

  private static Set<String> classNames(final Path jar) throws IOException {
    try (ZipFile file = new ZipFile(jar.toFile())) {
      final Set<String> found = new HashSet<>();
      file.stream()
          .map(ZipEntry::getName)
          .filter(entry -> entry.endsWith(CLASS_SUFFIX))
          .filter(entry -> !entry.startsWith(MULTI_RELEASE) && !entry.startsWith(MODULE_INFO))
          .map(
              entry -> entry.substring(0, entry.length() - CLASS_SUFFIX.length()).replace('/', '.'))
          .forEach(found::add);
      return found;
    }
  }

  private static Path indexFile(final Path jar, final Path directory) {
    return directory.resolve(INDEX_PREFIX + contentHash(jar) + INDEX_SUFFIX);
  }

  private static String contentHash(final Path jar) {
    try (InputStream in = Files.newInputStream(jar)) {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] buffer = new byte[BUFFER];
      int read = in.read(buffer);
      while (read >= 0) {
        digest.update(buffer, 0, read);
        read = in.read(buffer);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | NoSuchAlgorithmException unreadable) {
      return "unhashed";
    }
  }

  private static void store(final Path file, final Set<String> found) {
    try {
      Files.createDirectories(file.getParent());
      final Path temporary = Files.createTempFile(file.getParent(), INDEX_PREFIX, ".tmp");
      Files.write(temporary, List.copyOf(new TreeSet<>(found)), StandardCharsets.UTF_8);
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException unwritable) {
      LOGGER.log(Level.FINE, "event=jar-index-not-stored");
    }
  }

  @Override
  public boolean exists(final String name) {
    return names.contains(name);
  }

  @Override
  public Optional<JvmClass> find(final String name) {
    if (!names.contains(name)) {
      return Optional.empty();
    }
    try (ZipFile file = new ZipFile(jar.toFile())) {
      final ZipEntry entry = file.getEntry(name.replace('.', '/') + CLASS_SUFFIX);
      if (entry == null) {
        return Optional.empty();
      }
      try (InputStream in = file.getInputStream(entry)) {
        return Optional.of(ClassFileReader.read(in.readAllBytes()));
      }
    } catch (IOException | ClassFileException unreadable) {
      LOGGER.log(Level.FINE, "event=jar-class-unreadable");
      return Optional.empty();
    }
  }
}
