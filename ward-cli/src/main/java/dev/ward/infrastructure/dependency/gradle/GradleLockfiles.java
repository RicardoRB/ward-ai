package dev.ward.infrastructure.dependency.gradle;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencyScope;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the Gradle lockfiles of one project directory: {@code gradle.lockfile} lines are {@code
 * group:name:version=conf1,conf2}, and legacy {@code gradle/dependency-locks/<conf>.lockfile} files
 * list coordinates per configuration. The result maps each coordinate to its configurations.
 */
final class GradleLockfiles {
  private static final Pattern LOCK_LINE =
      Pattern.compile("^([^:=\\s]+):([^:=\\s]+):([^=\\s]+)(?:=(.*))?$");
  private static final int CONFIGURATIONS_GROUP = 4;
  private static final String LOCKFILE = "gradle.lockfile";
  private static final String LEGACY = "gradle/dependency-locks";

  private GradleLockfiles() {}

  /** Returns whether {@code dir} has a lockfile of either kind. */
  static boolean exists(final Path dir) {
    return Files.isRegularFile(dir.resolve(LOCKFILE)) || !legacy(dir).isEmpty();
  }

  /** Returns the project-relative place the lock was read from, for {@code module}. */
  static String source(final Path dir, final String module) {
    final String name = Files.isRegularFile(dir.resolve(LOCKFILE)) ? LOCKFILE : LEGACY;
    return module.isEmpty() ? name : module + "/" + name;
  }

  /**
   * Reads every coordinate locked in {@code dir} with its configurations.
   *
   * @throws DependencyInventoryException if a line is not a lock entry
   */
  static Map<String, Set<String>> read(final String project, final Path dir) {
    final Map<String, Set<String>> locked = new LinkedHashMap<>();
    try {
      final Path single = dir.resolve(LOCKFILE);
      if (Files.isRegularFile(single)) {
        Files.readAllLines(single).forEach(line -> entry(project, locked, line, ""));
      }
      for (final Path file : legacy(dir)) {
        final String configuration = LockFiles.fileName(file).replace(".lockfile", "");
        Files.readAllLines(file).forEach(line -> entry(project, locked, line, configuration));
      }
    } catch (IOException e) {
      throw new DependencyInventoryException(
          project, LOCKFILE, "it cannot be read", "check its permissions", e);
    }
    return locked;
  }

  private static List<Path> legacy(final Path dir) {
    final Path directory = dir.resolve(LEGACY);
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.toString().endsWith(".lockfile")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void entry(
      final String project,
      final Map<String, Set<String>> locked,
      final String line,
      final String configuration) {
    final String text = line.strip();
    if (text.isEmpty() || text.startsWith("#") || text.startsWith("empty=")) {
      return;
    }
    final Matcher matcher = LOCK_LINE.matcher(text);
    if (!matcher.matches()) {
      throw new DependencyInventoryException(
          project,
          LOCKFILE,
          "a line is not group:name:version=configurations",
          "run `gradle dependencies --write-locks`");
    }
    final String coordinate = matcher.group(1) + ":" + matcher.group(2) + ":" + matcher.group(3);
    final Set<String> configurations =
        locked.computeIfAbsent(coordinate, key -> new LinkedHashSet<>());
    if (configuration.isEmpty()) {
      if (matcher.group(CONFIGURATIONS_GROUP) != null) {
        configurations.addAll(List.of(matcher.group(CONFIGURATIONS_GROUP).split(",")));
      }
    } else {
      configurations.add(configuration);
    }
  }

  /** Processors are development, test-only is test, runtime is runtime, compile-only provided. */
  static DependencyScope scope(final Set<String> configurations) {
    return configurations.stream()
        .map(name -> scopeOf(name.toLowerCase(Locale.ROOT)))
        .reduce(DependencyScope::widest)
        .orElse(DependencyScope.RUNTIME);
  }

  private static DependencyScope scopeOf(final String name) {
    if (name.contains("annotationprocessor") || name.startsWith("kapt") || name.startsWith("ksp")) {
      return DependencyScope.DEVELOPMENT;
    }
    if (name.contains("test")) {
      return DependencyScope.TEST;
    }
    return name.endsWith("compileclasspath") || name.startsWith("compileonly")
        ? DependencyScope.PROVIDED
        : DependencyScope.RUNTIME;
  }
}
