package dev.ward.infrastructure.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copies the bundled type sidecar scripts into the project's cache. The cache directory is named by
 * the Ward version, so an upgrade never runs a stale script.
 */
public final class SidecarScripts {
  private static final Logger LOGGER = Logger.getLogger(SidecarScripts.class.getName());
  private static final String CACHE_DIRECTORY = ".ward/cache/sidecars";
  private static final String RESOURCE_PACKAGE = "/dev/ward/sidecar/";

  private SidecarScripts() {}

  /**
   * Writes the bundled script {@code name} under the cache of {@code projectRoot} for {@code
   * version} and returns its path; empty when the resource is missing or cannot be written.
   */
  public static Optional<Path> extract(
      final Path projectRoot, final String version, final String name) {
    final Path directory = projectRoot.resolve(CACHE_DIRECTORY).resolve(version);
    final Path target = directory.resolve(name);
    final URL resource = SidecarScripts.class.getResource(RESOURCE_PACKAGE + name);
    if (resource == null) {
      LOGGER.log(Level.WARNING, () -> "event=sidecar-script-missing script=" + name);
      return Optional.empty();
    }
    try (InputStream script = resource.openStream()) {
      Files.createDirectories(directory);
      Files.copy(script, target, StandardCopyOption.REPLACE_EXISTING);
      return Optional.of(target);
    } catch (IOException writeFailure) {
      LOGGER.log(Level.WARNING, () -> "event=sidecar-script-write-failure script=" + name);
      return Optional.empty();
    }
  }
}
