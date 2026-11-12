package dev.ward.infrastructure.dependency;

import dev.ward.domain.dependency.DependencyInventory;
import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.DependencySource;
import dev.ward.domain.dependency.GraphCache;
import dev.ward.domain.dependency.ResolvedGraphTool;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves a project with its build tool and keeps the graph under {@code .ward/cache/}, keyed by
 * the content of every build file that can change it, the stored format version and the identity of
 * the resolution tool. A stored graph that is missing, stale, corrupt or of another format is
 * replaced by resolving again and never changes the inventory returned.
 */
public final class ResolvingDependencySource implements DependencySource {
  private static final Logger LOGGER = Logger.getLogger(ResolvingDependencySource.class.getName());

  private final ResolvedGraphTool tool;
  private final Function<Project, List<Path>> buildFiles;
  private final String toolIdentity;
  private final GraphCache cache;
  private final JsonCodec codec;

  /**
   * Creates the source.
   *
   * @param tool resolves the graph
   * @param buildFiles lists the files that can change the graph, as absolute paths
   * @param toolIdentity names the resolution tool and its version, part of the key
   * @param cache stores the graphs
   * @param codec writes the stored document
   */
  public ResolvingDependencySource(
      final ResolvedGraphTool tool,
      final Function<Project, List<Path>> buildFiles,
      final String toolIdentity,
      final GraphCache cache,
      final JsonCodec codec) {
    this.tool = tool;
    this.buildFiles = buildFiles;
    this.toolIdentity = toolIdentity;
    this.cache = cache;
    this.codec = codec;
  }

  @Override
  public DependencyInventory read(final Project project) {
    final String key = key(project);
    final Optional<String> stored = cache.load(project.root(), key);
    if (stored.isPresent()) {
      try {
        final DependencyInventory inventory = InventoryJson.decode(codec, stored.get());
        LOGGER.log(
            Level.FINE,
            () -> "event=dependency-cache result=hit modules=" + inventory.modules().size());
        return inventory;
      } catch (IllegalArgumentException e) {
        LOGGER.log(Level.WARNING, "event=dependency-cache result=discarded reason=invalid");
      }
    } else {
      LOGGER.log(Level.FINE, "event=dependency-cache result=miss");
    }
    final DependencyInventory inventory = tool.resolve(project);
    cache.store(project.root(), key, InventoryJson.encode(codec, inventory));
    return inventory;
  }

  private String key(final Project project) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(
          (InventoryJson.FORMAT + "\n" + toolIdentity + "\n").getBytes(StandardCharsets.UTF_8));
      for (final Path file : buildFiles.apply(project).stream().sorted().toList()) {
        digest.update(
            project
                .root()
                .relativize(file)
                .toString()
                .replace('\\', '/')
                .getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
        digest.update((byte) 0);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          "build files",
          "they cannot be read",
          "check their permissions");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
    }
  }
}
