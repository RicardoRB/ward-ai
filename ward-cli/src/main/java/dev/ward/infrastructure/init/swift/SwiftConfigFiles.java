package dev.ward.infrastructure.init.swift;

import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigFiles;
import dev.ward.infrastructure.init.ConfigTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    retired(targets, root.resolve(SWIFTLINT), SWIFTLINT);
    retired(targets, root.resolve(STRICT_SWIFTLINT), STRICT_SWIFTLINT);
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
      retired(targets, manifest, "Package.swift");
    }
    return List.copyOf(targets);
  }

  /**
   * Adds a target that removes {@code file} when Ward wrote it, or keeps it as the user wrote it.
   * Adds nothing when the file does not exist, so init has nothing to report for it.
   */
  private static void retired(final List<ConfigTarget> targets, final Path file, final String name)
      throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    final String content = Files.readString(file);
    if (ConfigFiles.owned(content)) {
      targets.add(
          new ConfigTarget(
              file,
              ConfigTarget.Disposition.REMOVE,
              null,
              "removed: Ward's earlier " + name + "; the Ward rules replace SwiftLint"));
    } else {
      targets.add(
          ConfigTarget.keep(
              file, "kept: your " + name + " is no longer used; Ward runs its Swift rules"));
    }
  }
}
