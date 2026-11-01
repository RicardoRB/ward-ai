package dev.ward.infrastructure.finding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Swift tool output under fixtures/, captured from real runs, with the project root written as
 * {@code /repo}.
 */
final class SwiftFixtures {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final String CAPTURED_ROOT = "/repo";

  private SwiftFixtures() {}

  /** Returns fixture {@code name} with its captured project root replaced by {@code root}. */
  static String read(final String name, final Path root) {
    try {
      return Files.readString(FIXTURES.resolve(name)).replace(CAPTURED_ROOT, root.toString());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
