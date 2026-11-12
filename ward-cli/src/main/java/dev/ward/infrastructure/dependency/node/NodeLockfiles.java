package dev.ward.infrastructure.dependency.node;

import dev.ward.domain.dependency.DependencyInventoryException;
import dev.ward.domain.dependency.LockfileFormatException;
import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.dependency.LockFiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Finds and reads the lockfile of a Node project's package manager. */
final class NodeLockfiles {
  private final JsonCodec codec;

  NodeLockfiles(final JsonCodec codec) {
    this.codec = codec;
  }

  /** Returns the file names of the lockfile of {@code tool}, preferred first. */
  static List<String> names(final BuildTool tool) {
    return switch (tool) {
      case PNPM -> List.of("pnpm-lock.yaml");
      case YARN -> List.of("yarn.lock");
      case BUN -> List.of("bun.lock");
      default -> List.of("npm-shrinkwrap.json", "package-lock.json");
    };
  }

  /** Returns the lockfile name that exists in {@code dir}, if any. */
  static Optional<String> existing(final BuildTool tool, final Path dir) {
    return LockFiles.first(dir, names(tool));
  }

  /** Returns the directory with the lockfile: the project root or the nearest ancestor with one. */
  static Path root(final Project project) {
    return LockFiles.rootWith(project, names(project.buildTool()));
  }

  /**
   * Reads the lockfile at {@code file}.
   *
   * @throws DependencyInventoryException if it cannot be read or is not a format Ward reads
   */
  NodeLock parse(final Project project, final Path file, final String label) {
    final String text;
    try {
      text = Files.readString(file);
    } catch (IOException | UncheckedIOException e) {
      throw new DependencyInventoryException(
          project.root().toString(), label, "it cannot be read", "check the file's permissions", e);
    }
    try {
      return switch (project.buildTool()) {
        case PNPM -> PnpmLockfile.read(text);
        case YARN -> yarn(text);
        case BUN -> BunLockfile.read(text, codec);
        default -> NpmLockfile.read(text, codec);
      };
    } catch (LockfileFormatException e) {
      throw new DependencyInventoryException(
          project.root().toString(),
          label,
          e.getMessage(),
          "run `" + project.buildTool().executable() + " install` with a supported version",
          e);
    }
  }

  private static NodeLock yarn(final String text) {
    if (text.contains(YarnClassicLockfile.HEADER)) {
      return YarnClassicLockfile.read(text);
    }
    if (text.contains("__metadata:")) {
      return YarnBerryLockfile.read(text);
    }
    throw new LockfileFormatException("it is neither a Yarn classic nor a Yarn Berry lockfile");
  }
}
