package dev.ward.infrastructure.rule;

import dev.ward.analysis.domain.port.AnalysisCache;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores each cache value in its own file under a directory, named by its key. A value is written
 * to a temporary file and then moved into place, so a reader never sees a partial entry.
 */
public final class FileAnalysisCache implements AnalysisCache {
  private static final Logger LOGGER = Logger.getLogger(FileAnalysisCache.class.getName());
  private static final String SUFFIX = ".json";
  private static final String TEMPORARY_SUFFIX = ".tmp";

  private final Path directory;

  /** Creates a cache that keeps its entries in {@code directory}. */
  public FileAnalysisCache(final Path directory) {
    this.directory = directory;
  }

  @Override
  public Optional<String> read(final String key) {
    final Path file = directory.resolve(key + SUFFIX);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(file));
    } catch (final IOException exception) {
      LOGGER.log(Level.FINE, "Analysis cache entry is unreadable, analyzing again", exception);
      return Optional.empty();
    }
  }

  @Override
  public void write(final String key, final String value) {
    final Path file = directory.resolve(key + SUFFIX);
    try {
      Files.createDirectories(directory);
      final Path temporary = Files.createTempFile(directory, key, TEMPORARY_SUFFIX);
      Files.writeString(temporary, value);
      move(temporary, file);
    } catch (final IOException exception) {
      LOGGER.log(Level.FINE, "Analysis cache entry was not written", exception);
    }
  }

  private static void move(final Path temporary, final Path file) throws IOException {
    try {
      Files.move(
          temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (final AtomicMoveNotSupportedException exception) {
      Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
