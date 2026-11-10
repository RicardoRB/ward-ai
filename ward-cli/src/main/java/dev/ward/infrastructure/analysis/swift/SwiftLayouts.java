package dev.ward.infrastructure.analysis.swift;

import dev.ward.analysis.domain.swift.SwiftProjectLayout;
import dev.ward.analysis.domain.swift.SwiftTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Reads the modules of a Swift project root from its {@code Package.swift} and the {@code
 * project.pbxproj} of its Xcode projects. Reads only: no manifest runs, no process starts, and no
 * project file changes. A file that cannot be read is logged and leaves its targets out.
 */
public final class SwiftLayouts {

  private static final Logger LOGGER = Logger.getLogger(SwiftLayouts.class.getName());
  private static final String MANIFEST = "Package.swift";
  private static final String PROJECT_SUFFIX = ".xcodeproj";
  private static final String PROJECT_FILE = "project.pbxproj";
  private static final String SEPARATOR = "/";
  private static final Set<String> GENERATED_FOLDERS =
      Set.of(".build", ".ward", "DerivedData", "Pods", "Carthage");

  private SwiftLayouts() {}

  /** Tells whether the root holds a SwiftPM manifest or an Xcode project. */
  public static boolean isSwiftProject(final Path root) {
    return Files.isRegularFile(root.resolve(MANIFEST)) || !xcodeProjects(root).isEmpty();
  }

  /** Reads the layout of the root: the targets of its manifest, then of each Xcode project. */
  public static SwiftProjectLayout read(final Path root) {
    final List<SwiftTarget> targets = new ArrayList<>(packageTargets(root));
    for (final Path project : xcodeProjects(root)) {
      targets.addAll(xcodeTargets(project));
    }
    return new SwiftProjectLayout(targets.stream().map(SwiftLayouts::ownable).toList());
  }

  /** The target without the folders of build output, caches and vendored code. */
  private static SwiftTarget ownable(final SwiftTarget target) {
    final List<String> directories =
        target.directories().stream().filter(directory -> !isGenerated(directory)).toList();
    return new SwiftTarget(target.name(), directories, target.files(), target.dependencies());
  }

  private static boolean isGenerated(final String directory) {
    return Arrays.stream(directory.split(SEPARATOR)).anyMatch(GENERATED_FOLDERS::contains);
  }

  private static List<SwiftTarget> packageTargets(final Path root) {
    final Path manifest = root.resolve(MANIFEST);
    if (!Files.isRegularFile(manifest)) {
      return List.of();
    }
    try {
      final Optional<List<SwiftTarget>> targets =
          PackageManifestTargets.read(Files.readString(manifest, StandardCharsets.UTF_8));
      if (targets.isEmpty()) {
        LOGGER.warning("Package.swift has computed targets; its files count as implicit");
        return List.of();
      }
      return targets.get();
    } catch (final IOException exception) {
      LOGGER.warning("Package.swift could not be read; its files count as implicit");
      return List.of();
    }
  }

  private static List<SwiftTarget> xcodeTargets(final Path project) {
    try {
      return XcodeProjectTargets.read(Files.readString(project, StandardCharsets.UTF_8));
    } catch (final IOException | IllegalArgumentException exception) {
      LOGGER.warning("An Xcode project file could not be read; its files count as implicit");
      return List.of();
    }
  }

  /** The {@code project.pbxproj} files of the Xcode projects directly under the root, sorted. */
  private static List<Path> xcodeProjects(final Path root) {
    try (Stream<Path> children = Files.list(root)) {
      return children
          .filter(child -> child.toString().endsWith(PROJECT_SUFFIX))
          .map(child -> child.resolve(PROJECT_FILE))
          .filter(Files::isRegularFile)
          .sorted()
          .toList();
    } catch (final IOException exception) {
      return List.of();
    }
  }
}
