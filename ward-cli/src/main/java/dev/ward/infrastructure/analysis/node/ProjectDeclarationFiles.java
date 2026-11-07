package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.node.types.DeclarationFiles;
import dev.ward.analysis.domain.types.CachedTypeFacts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * The files of a project as the type resolver reads them. A path that leaves the project root, by
 * {@code ..} or by a link, is not readable. A package is hashed over its manifest and its
 * declaration files.
 */
public final class ProjectDeclarationFiles implements DeclarationFiles {
  private static final Logger LOGGER = Logger.getLogger(ProjectDeclarationFiles.class.getName());
  private static final long MAX_BYTES = 16L * 1024 * 1024;
  private static final String NODE_MODULES = "node_modules";

  private final Path root;
  private final Map<String, Optional<String>> hashes = new ConcurrentHashMap<>();

  /** Creates the files of the project at {@code root}. */
  public ProjectDeclarationFiles(final Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  private Optional<Path> inside(final String path) {
    if (path.isEmpty() || path.startsWith("/") || path.contains("..")) {
      return Optional.empty();
    }
    final Path resolved = root.resolve(path).normalize();
    if (!resolved.startsWith(root)) {
      return Optional.empty();
    }
    try {
      return Files.exists(resolved) && resolved.toRealPath().startsWith(root.toRealPath())
          ? Optional.of(resolved)
          : Optional.empty();
    } catch (IOException unreadable) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> read(final String path) {
    return inside(path)
        .filter(Files::isRegularFile)
        .flatMap(
            file -> {
              try {
                return Files.size(file) > MAX_BYTES
                    ? Optional.empty()
                    : Optional.of(Files.readString(file, StandardCharsets.UTF_8));
              } catch (IOException | RuntimeException unreadable) {
                LOGGER.log(Level.FINE, "event=declaration-file-unreadable");
                return Optional.empty();
              }
            });
  }

  @Override
  public boolean exists(final String path) {
    return inside(path).filter(Files::isRegularFile).isPresent();
  }

  @Override
  public List<String> list(final String directory) {
    return inside(directory)
        .filter(Files::isDirectory)
        .map(
            dir -> {
              try (Stream<Path> entries = Files.list(dir)) {
                return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
              } catch (IOException unreadable) {
                return List.<String>of();
              }
            })
        .orElse(List.of());
  }

  @Override
  public Optional<String> packageHash(final String directory) {
    return hashes.computeIfAbsent(directory, this::hash);
  }

  private Optional<String> hash(final String directory) {
    final Optional<Path> dir = inside(directory).filter(Files::isDirectory);
    if (dir.isEmpty()) {
      return Optional.empty();
    }
    try {
      final List<Path> files = declarations(dir.get());
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (final Path file : files) {
        digest.update(dir.get().relativize(file).toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
        digest.update((byte) 0);
      }
      return Optional.of(HexFormat.of().formatHex(digest.digest()));
    } catch (IOException | NoSuchAlgorithmException unreadable) {
      LOGGER.log(Level.FINE, "event=package-hash-failed");
      return Optional.empty();
    }
  }

  private static List<Path> declarations(final Path directory) throws IOException {
    final List<Path> found = new ArrayList<>();
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(
              final Path dir, final BasicFileAttributes attrs) {
            final boolean nested =
                !dir.equals(directory) && NODE_MODULES.equals(dir.getFileName().toString());
            return nested ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
            final String name = file.getFileName().toString();
            if (attrs.isRegularFile()
                && ("package.json".equals(name)
                    || name.endsWith(".d.ts")
                    || name.endsWith(".d.mts")
                    || name.endsWith(".d.cts"))) {
              found.add(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    found.sort(null);
    return found;
  }

  /** Returns the SHA-256 of {@code text} in hex. */
  static String sha256(final String text) {
    return CachedTypeFacts.sha256(text);
  }
}
