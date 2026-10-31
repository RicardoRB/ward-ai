package dev.ward.infrastructure.project;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.TaskRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a Swift project's build tool and target Swift version from its directory listing. */
final class SwiftProjectInspection {
  private static final String PACKAGE_SWIFT = "Package.swift";
  private static final String XCODEPROJ = ".xcodeproj";
  private static final String XCWORKSPACE = ".xcworkspace";
  private static final String PBXPROJ = "project.pbxproj";
  private static final String PODFILE = "Podfile";
  private static final List<String> TUIST_FILES = List.of("Project.swift", "Workspace.swift");
  private static final String XCODEGEN_FILE = "project.yml";
  private static final String DEFAULT_SWIFT_VERSION = "6.2";
  private static final Pattern TOOLS_VERSION =
      Pattern.compile("swift-tools-version\\s*:\\s*(\\d+\\.\\d+)");
  private static final Pattern SWIFT_VERSION =
      Pattern.compile("SWIFT_VERSION\\s*=\\s*\"?(\\d{1,9})\\.(\\d{1,9})\"?\\s*;");
  private static final Logger LOGGER = Logger.getLogger(SwiftProjectInspection.class.getName());

  private SwiftProjectInspection() {}

  /**
   * Returns the Swift project at {@code dir}, whose entries are {@code names}, or empty when no
   * Swift marker is present. A {@code Podfile} is reported as the wrapper flag.
   */
  static Optional<Project> project(final Path dir, final List<String> names) {
    return buildTool(dir, names)
        .map(
            tool ->
                new Project(
                    dir,
                    Language.SWIFT,
                    tool,
                    Framework.NONE,
                    names.contains(PODFILE),
                    false,
                    dir,
                    Set.of(Language.SWIFT),
                    version(dir, names, tool),
                    TaskRunner.NONE));
  }

  /** The first rule that matches decides: Xcode, SwiftPM, Tuist, then XcodeGen. */
  private static Optional<BuildTool> buildTool(final Path dir, final List<String> names) {
    final Optional<String> xcode =
        names.stream().filter(name -> isXcodeBundle(dir, name)).sorted().findFirst();
    if (xcode.isPresent()) {
      return Optional.of(decided(xcode.get(), BuildTool.XCODE));
    }
    if (names.contains(PACKAGE_SWIFT)) {
      return Optional.of(decided(PACKAGE_SWIFT, BuildTool.SWIFTPM));
    }
    final Optional<String> tuist = TUIST_FILES.stream().filter(names::contains).findFirst();
    if (tuist.isPresent()) {
      return Optional.of(decided(tuist.get(), BuildTool.TUIST));
    }
    return names.contains(XCODEGEN_FILE)
        ? Optional.of(decided(XCODEGEN_FILE, BuildTool.XCODEGEN))
        : Optional.empty();
  }

  private static boolean isXcodeBundle(final Path dir, final String name) {
    return (name.endsWith(XCODEPROJ) || name.endsWith(XCWORKSPACE))
        && Files.isDirectory(dir.resolve(name));
  }

  private static BuildTool decided(final String source, final BuildTool tool) {
    LOGGER.fine(() -> "Swift build tool " + tool.displayName() + " from " + source);
    return tool;
  }

  /** The target version: the manifest's tools version or the project's build setting. */
  private static String version(final Path dir, final List<String> names, final BuildTool tool) {
    final Optional<String> declared =
        switch (tool) {
          case SWIFTPM -> toolsVersion(dir.resolve(PACKAGE_SWIFT));
          case XCODE -> buildSettingVersion(dir, names);
          default -> Optional.empty();
        };
    return declared.orElse(DEFAULT_SWIFT_VERSION);
  }

  private static Optional<String> toolsVersion(final Path manifest) {
    final Matcher matcher = TOOLS_VERSION.matcher(readText(manifest));
    return matcher.find()
        ? Optional.of(sourced(PACKAGE_SWIFT, matcher.group(1)))
        : Optional.empty();
  }

  /** The highest {@code SWIFT_VERSION} in the first Xcode project; Ward never edits the file. */
  private static Optional<String> buildSettingVersion(final Path dir, final List<String> names) {
    return names.stream()
        .filter(name -> name.endsWith(XCODEPROJ))
        .sorted()
        .findFirst()
        .flatMap(
            project ->
                SWIFT_VERSION
                    .matcher(readText(dir.resolve(project).resolve(PBXPROJ)))
                    .results()
                    .map(found -> new int[] {toInt(found.group(1)), toInt(found.group(2))})
                    .max(Comparator.<int[]>comparingInt(v -> v[0]).thenComparingInt(v -> v[1]))
                    .map(v -> sourced(project + "/" + PBXPROJ, v[0] + "." + v[1])));
  }

  private static int toInt(final String digits) {
    return Integer.parseInt(digits);
  }

  private static String sourced(final String source, final String version) {
    LOGGER.fine(() -> "Swift version " + version + " from " + source);
    return version;
  }

  /** Returns the file's text, or an empty string when it is missing or unreadable. */
  private static String readText(final Path file) {
    if (!Files.isRegularFile(file)) {
      return "";
    }
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return "";
    }
  }
}
