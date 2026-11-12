package dev.ward.infrastructure.dependency;

import dev.ward.domain.dependency.GraphCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Stores resolved graphs under {@code .ward/cache/dependencies/<key>.json} of the project. An entry
 * older than {@link #FRESHNESS}, by the file's modification time, counts as missing, so dynamic and
 * snapshot versions are resolved again at least daily. Writes go to a temporary file that is then
 * moved into place.
 */
public final class FileGraphCache implements GraphCache {
  /** How long a stored graph is reused. */
  public static final Duration FRESHNESS = Duration.ofHours(24);

  private static final Logger LOGGER = Logger.getLogger(FileGraphCache.class.getName());
  private static final Path DIRECTORY = Path.of(".ward", "cache", "dependencies");

  private final Clock clock;

  /** Creates the cache on the system clock. */
  @Inject
  public FileGraphCache() {
    this(Clock.systemUTC());
  }

  /** Creates the cache on {@code clock}, which tests move. */
  public FileGraphCache(final Clock clock) {
    this.clock = clock;
  }

  @Override
  public Optional<String> load(final Path projectRoot, final String key) {
    final Path file = file(projectRoot, key);
    try {
      if (!Files.isRegularFile(file)) {
        return Optional.empty();
      }
      final Instant written = Files.getLastModifiedTime(file).toInstant();
      if (Duration.between(written, clock.instant()).compareTo(FRESHNESS) > 0) {
        LOGGER.log(Level.FINE, "event=dependency-cache result=stale");
        return Optional.empty();
      }
      return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException | RuntimeException e) {
      LOGGER.log(Level.WARNING, "event=dependency-cache result=unreadable");
      return Optional.empty();
    }
  }

  @Override
  public void store(final Path projectRoot, final String key, final String text) {
    final Path file = file(projectRoot, key);
    try {
      Files.createDirectories(file.getParent());
      final Path temporary = Files.createTempFile(file.getParent(), "graph", ".tmp");
      Files.writeString(temporary, text, StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(clock.instant()));
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "event=dependency-cache result=not-written");
    }
  }

  private static Path file(final Path projectRoot, final String key) {
    return projectRoot.resolve(DIRECTORY).resolve(key + ".json");
  }
}
