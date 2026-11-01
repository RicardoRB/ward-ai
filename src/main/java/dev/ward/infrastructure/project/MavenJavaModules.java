package dev.ward.infrastructure.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the modules a Maven aggregator lists that own Java sources, so init can place module gates
 * where the code is. A module with no Java source directory is left out, and {@link #skipped} names
 * it.
 */
public final class MavenJavaModules {
  private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<\\s]+)\\s*</module>");
  private static final List<String> SOURCES = List.of("src/main/java", "src/test/java");

  private MavenJavaModules() {}

  /**
   * Returns the {@code pom.xml} of each module of the aggregator at {@code root} that has Java
   * sources, in declaration order; empty when {@code root} lists no modules.
   */
  public static List<Path> of(final Path root) {
    return declared(root).stream()
        .filter(MavenJavaModules::hasJavaSources)
        .map(dir -> dir.resolve("pom.xml"))
        .toList();
  }

  /** Returns the root-relative paths of the listed modules that have no Java sources. */
  public static List<String> skipped(final Path root) {
    return declared(root).stream()
        .filter(dir -> !hasJavaSources(dir))
        .map(dir -> root.relativize(dir).toString())
        .toList();
  }

  /** Returns the module directories the aggregator lists whose {@code pom.xml} exists. */
  private static List<Path> declared(final Path root) {
    final Path pom = root.resolve("pom.xml");
    if (!Files.isRegularFile(pom)) {
      return List.of();
    }
    return MODULE
        .matcher(read(pom))
        .results()
        .map(match -> root.resolve(match.group(1)).normalize())
        .filter(dir -> Files.isRegularFile(dir.resolve("pom.xml")))
        .toList();
  }

  private static boolean hasJavaSources(final Path dir) {
    return SOURCES.stream().anyMatch(source -> Files.isDirectory(dir.resolve(source)));
  }

  private static String read(final Path pom) {
    try {
      return Files.readString(pom);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read Maven build file " + pom, e);
    }
  }
}
