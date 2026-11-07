package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.node.types.DeclarationCache;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Keeps declaration indexes in files under a directory, named by the hash of their key. */
public final class DiskDeclarationCache implements DeclarationCache {
  private static final Logger LOGGER = Logger.getLogger(DiskDeclarationCache.class.getName());

  private final Path directory;

  /** Creates the cache in {@code directory}, which is created when the first index is stored. */
  public DiskDeclarationCache(final Path directory) {
    this.directory = directory;
  }

  private Path file(final String key) {
    return directory.resolve(CachedTypeFacts.sha256(key) + ".bin");
  }

  @Override
  public Optional<byte[]> load(final String key) {
    final Path file = file(key);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readAllBytes(file));
    } catch (IOException unreadable) {
      LOGGER.log(Level.FINE, "event=declaration-cache-unreadable");
      return Optional.empty();
    }
  }

  @Override
  public void store(final String key, final byte[] bytes) {
    try {
      Files.createDirectories(directory);
      final Path temporary = Files.createTempFile(directory, "index", ".tmp");
      Files.write(temporary, bytes);
      Files.move(temporary, file(key), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException unwritable) {
      LOGGER.log(Level.WARNING, "event=declaration-cache-unwritable");
    }
  }
}
