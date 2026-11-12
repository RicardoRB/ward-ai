package dev.ward.infrastructure.dependency.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Finds the Gradle projects below a root and the build files that decide their dependencies. */
final class GradleModules {
  private static final int MAX_DEPTH = 6;
  private static final Set<String> SCRIPTS = Set.of("build.gradle.kts", "build.gradle");
  private static final Set<String> SKIPPED = Set.of("build", "node_modules", "buildSrc", "out");

  private GradleModules() {}

  /** Returns the project directories, as paths relative to {@code root}; the root is always one. */
  static List<String> paths(final Path root) {
    final List<String> paths = new ArrayList<>();
    paths.add("");
    try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
      walk.filter(Files::isDirectory)
          .filter(dir -> !dir.equals(root) && !skipped(root, dir) && buildScript(dir) != null)
          .map(dir -> root.relativize(dir).toString().replace('\\', '/'))
          .sorted()
          .forEach(paths::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return paths;
  }

  /** Returns the build script of {@code dir}, preferring the Kotlin one, or null. */
  static String buildScript(final Path dir) {
    for (final String name : List.of("build.gradle.kts", "build.gradle")) {
      if (Files.isRegularFile(dir.resolve(name))) {
        return name;
      }
    }
    return null;
  }

  /**
   * Returns the files that can change a resolved graph: build scripts, settings scripts, {@code
   * gradle.properties}, version catalogs and the wrapper properties.
   */
  static List<Path> buildFiles(final Path root) {
    final List<Path> files = new ArrayList<>();
    for (final String module : paths(root)) {
      final Path dir = module.isEmpty() ? root : root.resolve(module);
      SCRIPTS.stream().map(dir::resolve).filter(Files::isRegularFile).forEach(files::add);
    }
    for (final String name :
        List.of(
            "settings.gradle",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle/wrapper/gradle-wrapper.properties")) {
      final Path file = root.resolve(name);
      if (Files.isRegularFile(file)) {
        files.add(file);
      }
    }
    final Path gradle = root.resolve("gradle");
    if (Files.isDirectory(gradle)) {
      try (Stream<Path> catalogs = Files.list(gradle)) {
        catalogs
            .filter(file -> file.getFileName().toString().endsWith(".versions.toml"))
            .forEach(files::add);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return files;
  }

  private static boolean skipped(final Path root, final Path dir) {
    for (final Path part : root.relativize(dir)) {
      final String name = part.toString();
      if (name.startsWith(".") || SKIPPED.contains(name)) {
        return true;
      }
    }
    return false;
  }
}
