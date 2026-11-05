package dev.ward.bench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Adds a {@code ward} check that runs alone, in the probe profile, to a working copy's {@code
 * ward.toml}. Its {@code paths} table matches the sources of every analysis language, so an edit to
 * any of them narrows it, as an agent's edit profile does.
 */
final class WardProbe {
  static final String CHECK = "ward-gate-edit";

  private static final String SECTION =
      """

      [checks.ward-gate-edit]
      uses = "ward"
      profiles = ["ward-gate-edit"]
      [checks.ward-gate-edit.paths]
      match = ["**/*.java", "**/*.kt", "**/*.ts", "**/*.tsx", "**/*.js",
               "**/*.jsx", "**/*.py", "**/*.swift"]
      full = ["ward.toml"]
      """;

  private WardProbe() {}

  /** Appends the probe check to the copy's {@code ward.toml}; the copy is never the checkout. */
  static void install(final Path copy) {
    try {
      Files.writeString(
          copy.resolve("ward.toml"), SECTION, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not add the ward probe to the working copy", e);
    }
  }
}
