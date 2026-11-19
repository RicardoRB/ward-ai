package dev.ward.infrastructure.project.rust;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.project.WorkspaceGlobs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** Reads a Cargo project's workspace members from {@code Cargo.toml}; never runs cargo. */
public final class CargoWorkspaces {
  /** The Cargo manifest, which marks a Rust project. */
  public static final String MANIFEST = "Cargo.toml";

  private static final String MEMBERS = "workspace.members";
  private static final String EXCLUDE = "workspace.exclude";

  private CargoWorkspaces() {}

  /** Returns the Rust project whose manifest is in {@code dir}. */
  public static Project project(final Path dir) {
    return new Project(dir, Language.RUST, BuildTool.CARGO, Framework.NONE);
  }

  /**
   * Returns whether {@code candidate} is a member of the workspace at {@code root}: it has a
   * manifest, is listed in {@code workspace.members} and is not in {@code workspace.exclude}.
   */
  public static boolean isMember(final Path root, final Path candidate) {
    if (!Files.isRegularFile(candidate.resolve(MANIFEST))) {
      return false;
    }
    final TomlTable manifest = manifest(root);
    return WorkspaceGlobs.matchesAny(globs(manifest, MEMBERS), root, candidate)
        && !WorkspaceGlobs.matchesAny(globs(manifest, EXCLUDE), root, candidate);
  }

  /**
   * Returns the member directories of the workspace at {@code root}, sorted and without {@code
   * root}; empty when it is not a workspace root or cannot be read.
   */
  public static List<Path> members(final Path root) {
    if (!manifest(root).contains("workspace")) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root, FileSystemProjectInspection.MAX_SEARCH_DEPTH)) {
      return files
          .filter(Files::isDirectory)
          .filter(dir -> !dir.equals(root) && isMember(root, dir))
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }

  /** Parses the manifest leniently: a missing, unreadable or malformed file is an empty table. */
  public static TomlTable manifest(final Path dir) {
    final Path file = dir.resolve(MANIFEST);
    if (!Files.isRegularFile(file)) {
      return Toml.parse("");
    }
    try {
      final TomlParseResult result = Toml.parse(Files.readString(file));
      return result.hasErrors() ? Toml.parse("") : result;
    } catch (IOException e) {
      return Toml.parse("");
    }
  }

  private static List<String> globs(final TomlTable manifest, final String key) {
    return manifest.getArrayOrEmpty(key).toList().stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
  }
}
