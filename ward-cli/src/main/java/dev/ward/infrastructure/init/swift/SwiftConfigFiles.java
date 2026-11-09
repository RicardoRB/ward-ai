package dev.ward.infrastructure.init.swift;

import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigFiles;
import dev.ward.infrastructure.init.ConfigTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The files an earlier init wrote for SwiftLint: its config, its strict layer and the Ward-owned
 * tools package. Init no longer writes them, so Ward-owned copies are removed and user-owned ones
 * are kept as written.
 */
public final class SwiftConfigFiles {
  static final String SWIFTLINT = ".swiftlint.yml";
  static final String STRICT_SWIFTLINT = ".ward/swiftlint-strict.yml";
  static final String TOOLS_DIRECTORY = ".ward/swift-tools";
  static final String TOOLS_MANIFEST = TOOLS_DIRECTORY + "/Package.swift";

  private SwiftConfigFiles() {}

  /** Returns the earlier SwiftLint files of a Swift project, each removed or kept, in order. */
  public static List<ConfigTarget> targets(final Project project) throws IOException {
    final Path root = project.root();
    final List<ConfigTarget> targets = new ArrayList<>();
    retired(root.resolve(SWIFTLINT)).ifPresent(targets::add);
    retired(root.resolve(STRICT_SWIFTLINT)).ifPresent(targets::add);
    final Path manifest = root.resolve(TOOLS_MANIFEST);
    if (Files.isRegularFile(manifest) && ConfigFiles.owned(Files.readString(manifest))) {
      // The lock file and build cache belong to that manifest, so they go with it.
      targets.add(
          new ConfigTarget(
              root.resolve(TOOLS_DIRECTORY),
              ConfigTarget.Disposition.REMOVE,
              null,
              "removed: Ward's earlier SwiftLint tools package and its build cache"));
    } else {
      retired(manifest).ifPresent(targets::add);
    }
    return List.copyOf(targets);
  }

  /**
   * Returns a target that removes {@code file} when Ward wrote it, or keeps it as the user wrote
   * it. Empty when the file does not exist, so init has nothing to report for it.
   */
  private static Optional<ConfigTarget> retired(final Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    final String name = file.getFileName().toString();
    return Optional.of(
        ConfigFiles.owned(Files.readString(file))
            ? new ConfigTarget(
                file,
                ConfigTarget.Disposition.REMOVE,
                null,
                "removed: Ward's earlier " + name + "; the Ward rules replace SwiftLint")
            : ConfigTarget.keep(
                file, "kept: your " + name + " is no longer used; Ward runs its Swift rules"));
  }
}
