package dev.ward.infrastructure.init.swift;

import dev.ward.domain.config.WardException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** The file operations of the Harmonize rules generator, which write and remove Ward's files. */
final class HarmonizeRulesFiles {
  private HarmonizeRulesFiles() {}

  static void removeWardRootConfig(final Path root) {
    final Path config = root.resolve(HarmonizeRulesSource.ROOT_CONFIG);
    if (Files.isRegularFile(config)
        && read(config).startsWith("# " + HarmonizeRulesSource.MARKER)) {
      delete(config);
    }
  }

  /** Deletes {@code directory} and everything under it, deepest paths first. */
  static void removeTree(final Path directory) {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(HarmonizeRulesFiles::delete);
    } catch (IOException e) {
      throw new WardException("Could not remove " + directory + ": " + e.getMessage(), e);
    }
  }

  static void delete(final Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new WardException("Could not remove " + file + ": " + e.getMessage(), e);
    }
  }

  static void write(final Path file, final String content) {
    try {
      final Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, content);
    } catch (IOException e) {
      throw new WardException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }

  static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
