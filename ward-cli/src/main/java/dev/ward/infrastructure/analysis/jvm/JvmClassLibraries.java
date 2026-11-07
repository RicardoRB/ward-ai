package dev.ward.infrastructure.analysis.jvm;

import dev.ward.analysis.domain.jvm.types.JvmClass;
import dev.ward.analysis.domain.jvm.types.JvmLibrary;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The compiled classes of one project: the jars and directories of its {@code java-classpath}, then
 * the JDK image found through {@code JAVA_HOME} or the running JDK. An input that is missing or
 * unreadable is left out, so its classes are unknown. Parsed classes are remembered for the run.
 */
public final class JvmClassLibraries implements JvmLibrary, AutoCloseable {
  private static final Logger LOGGER = Logger.getLogger(JvmClassLibraries.class.getName());
  private static final String JAR_SUFFIX = ".jar";
  private static final String ZIP_SUFFIX = ".zip";

  private final List<JvmLibrary> libraries;
  private final List<Closeable> owned;
  private final ConcurrentMap<String, Optional<JvmClass>> parsed = new ConcurrentHashMap<>();

  private JvmClassLibraries(final List<JvmLibrary> libraries, final List<Closeable> owned) {
    this.libraries = List.copyOf(libraries);
    this.owned = List.copyOf(owned);
  }

  /**
   * Opens the libraries of a project.
   *
   * @param projectRoot the root that relative classpath entries resolve against
   * @param classpath the classpath entries, absolute or relative to {@code projectRoot}
   * @param javaHome the JDK home, empty to find none
   * @param cacheDirectory where jar indexes are cached, empty for no cache
   * @return the libraries; classpath entries first, the JDK image last
   */
  public static JvmClassLibraries open(
      final Path projectRoot,
      final List<String> classpath,
      final Optional<Path> javaHome,
      final Optional<Path> cacheDirectory) {
    final List<JvmLibrary> libraries = new ArrayList<>();
    final List<Closeable> owned = new ArrayList<>();
    for (final String entry : classpath) {
      final Path path = projectRoot.resolve(entry).toAbsolutePath().normalize();
      addEntry(path, cacheDirectory, libraries);
    }
    javaHome
        .map(home -> home.resolve("lib").resolve("modules"))
        .flatMap(JdkImageLibrary::open)
        .ifPresent(
            image -> {
              libraries.add(image);
              owned.add(image);
            });
    if (LOGGER.isLoggable(Level.FINE)) {
      LOGGER.fine("event=jvm-libraries-opened count=" + libraries.size());
    }
    return new JvmClassLibraries(libraries, owned);
  }

  private static void addEntry(
      final Path path, final Optional<Path> cacheDirectory, final List<JvmLibrary> libraries) {
    final String name = path.getFileName() == null ? "" : path.getFileName().toString();
    if (Files.isDirectory(path)) {
      libraries.add(new DirectoryLibrary(path));
    } else if (Files.isRegularFile(path)
        && (name.endsWith(JAR_SUFFIX) || name.endsWith(ZIP_SUFFIX))) {
      JarLibrary.open(path, cacheDirectory).ifPresent(libraries::add);
    }
  }

  /**
   * Finds the JDK to read: {@code JAVA_HOME} if its image exists, else the running JDK's home.
   *
   * @param environmentHome the value of {@code JAVA_HOME}, empty when unset
   * @param runningHome the {@code java.home} of this process, empty when unset
   */
  public static Optional<Path> jdkHome(
      final Optional<String> environmentHome, final Optional<String> runningHome) {
    return java.util.stream.Stream.concat(environmentHome.stream(), runningHome.stream())
        .filter(home -> !home.isBlank())
        .map(Path::of)
        .filter(home -> Files.isRegularFile(home.resolve("lib").resolve("modules")))
        .findFirst();
  }

  @Override
  public boolean exists(final String name) {
    return libraries.stream().anyMatch(library -> library.exists(name));
  }

  @Override
  public Optional<JvmClass> find(final String name) {
    return parsed.computeIfAbsent(
        name,
        key ->
            libraries.stream()
                .filter(library -> library.exists(key))
                .findFirst()
                .flatMap(library -> library.find(key)));
  }

  @Override
  public void close() {
    owned.forEach(JvmClassLibraries::closeQuietly);
  }

  private static void closeQuietly(final Closeable library) {
    try {
      library.close();
    } catch (IOException failed) {
      LOGGER.log(Level.FINE, "event=library-close-failed");
    }
  }
}
