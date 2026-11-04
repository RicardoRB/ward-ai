package dev.ward.infrastructure.analysis.node;

import dev.ward.analysis.domain.node.NodeProjectLayout.NodePackage;
import dev.ward.infrastructure.project.node.NodeProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Resolves an {@code extends} value of a tsconfig to the file it names. */
final class NodeExtendsTargets {
  private static final String DOT = ".";
  private static final String SEPARATOR = "/";
  private static final String SCOPE = "@";
  private static final String JSON_EXTENSION = ".json";

  private NodeExtendsTargets() {}

  /**
   * Resolves an {@code extends} value to a file: a relative path, or a workspace package's file.
   */
  static Optional<Path> resolve(
      final Path root, final Path dir, final String extended, final List<NodePackage> packages) {
    if (extended.startsWith(DOT) || extended.startsWith(SEPARATOR)) {
      final Path path = dir.resolve(extended).normalize();
      return existing(path).or(() -> existing(Path.of(path + JSON_EXTENSION)));
    }
    final int segments = extended.startsWith(SCOPE) ? 2 : 1;
    final String[] parts = extended.split(SEPARATOR, segments + 1);
    if (parts.length < segments) {
      return Optional.empty();
    }
    final String name = String.join(SEPARATOR, List.of(parts).subList(0, segments));
    final String rest = parts.length > segments ? parts[segments] : "";
    return packages.stream()
        .filter(pkg -> pkg.name().equals(name))
        .findFirst()
        .flatMap(pkg -> packageTarget(root.resolve(pkg.directory()), rest));
  }

  private static Optional<Path> packageTarget(final Path packageDirectory, final String rest) {
    if (rest.isEmpty()) {
      return existing(packageDirectory.resolve(NodeProjectInspection.TSCONFIG));
    }
    return existing(packageDirectory.resolve(rest))
        .or(() -> existing(packageDirectory.resolve(rest + JSON_EXTENSION)));
  }

  private static Optional<Path> existing(final Path path) {
    return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
  }
}
