package dev.ward.infrastructure.project;

import dev.ward.domain.config.WardException;
import dev.ward.domain.project.SwiftToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/**
 * Finds a Swift tool's own config in the project root. A {@code .swiftlint.yml} that Ward wrote
 * (its marker is the first line) does not count; any {@code .swift-format} does.
 */
public final class FileSystemSwiftToolConfigs implements SwiftToolConfigs {
  private static final String SWIFTLINT = ".swiftlint.yml";
  private static final String WARD_MARKER = "# managed-by: ward";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public FileSystemSwiftToolConfigs() {
    // Stateless; the constructor exists for Dagger.
  }

  @Override
  public Optional<Path> userConfig(final Path root, final String fileName) {
    final Path file = root.resolve(fileName);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    return SWIFTLINT.equals(fileName) && wardOwned(file) ? Optional.empty() : Optional.of(file);
  }

  private static boolean wardOwned(final Path file) {
    try {
      return Files.readString(file).startsWith(WARD_MARKER);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
  }
}
