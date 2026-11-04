package dev.ward.infrastructure.init.swift;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The Swift architecture fixtures and the seeded violation the Harmonize rules must reject. Both
 * the offline layer verdict test and the opt-in Harmonize smoke test read these files, so they
 * check the same code.
 */
final class SwiftArchitectureFiles {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/swift-architecture");
  private static final String SEPARATOR = "/";

  private SwiftArchitectureFiles() {}

  /** Returns the files of the {@code style} fixture, by path relative to the project root. */
  static Map<String, String> fixture(final String style) throws IOException {
    final Path root = FIXTURES.resolve(style);
    final Map<String, String> files = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (final Path file : paths.filter(Files::isRegularFile).toList()) {
        final String relative = root.relativize(file).toString().replace('\\', '/');
        files.put(relative, Files.readString(file));
      }
    }
    return files;
  }

  /**
   * Returns a file in {@code source} that names a type declared under {@code forbidden}, and the
   * declaring file, by path relative to the project root.
   */
  static Map<String, String> violation(final String source, final String forbidden) {
    final String app = "App" + SEPARATOR;
    return Map.of(
        app + forbidden + SEPARATOR + "ViolationRecord.swift",
        "struct ViolationRecord {}\n",
        app + source + SEPARATOR + "Violation.swift",
        "struct Violation {\n    let record: ViolationRecord\n}\n");
  }
}
