package dev.ward.infrastructure.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads npm, pnpm, Yarn and Bun workspace patterns and matches member directories against them. */
final class NodeWorkspaces {
  private static final String PNPM_WORKSPACE = "pnpm-workspace.yaml";
  private static final String PACKAGES = "packages";
  private static final Pattern LIST_ITEM = Pattern.compile("^\\s*-\\s*(.+?)\\s*$");
  private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^\\S.*:.*$");

  private NodeWorkspaces() {}

  /**
   * Returns whether {@code candidate} is listed by the workspace patterns of {@code root}: the
   * {@code workspaces} field of its package.json, or its {@code pnpm-workspace.yaml}.
   */
  static boolean isMember(final Path root, final Path candidate) {
    return hasProjectFile(candidate)
        && !hasOtherBuildFile(candidate)
        && listsByPattern(root, candidate);
  }

  /** Returns whether the workspace patterns of {@code root} match {@code candidate}. */
  static boolean listsByPattern(final Path root, final Path candidate) {
    return WorkspaceGlobs.matchesAny(
        patterns(root, NodeProjectInspection.manifest(root)), root, candidate);
  }

  /** Returns whether a directory matched by the workspace patterns has {@code tsconfig.json}. */
  static boolean hasTypeScriptMember(final Path root, final Map<?, ?> manifest) {
    final List<String> patterns = patterns(root, manifest);
    if (patterns.isEmpty()) {
      return false;
    }
    try (Stream<Path> files = Files.walk(root, FileSystemProjectInspection.MAX_SEARCH_DEPTH)) {
      return files
          .filter(file -> file.endsWith(NodeProjectInspection.TSCONFIG))
          .map(Path::getParent)
          .filter(member -> !member.toString().contains("node_modules"))
          .anyMatch(member -> WorkspaceGlobs.matchesAny(patterns, root, member));
    } catch (IOException | UncheckedIOException e) {
      return false;
    }
  }

  /** Returns the member directories of {@code root} that hold package.json or tsconfig.json. */
  static List<Path> memberDirectories(final Path root, final Map<?, ?> manifest) {
    return directories(root, manifest, false);
  }

  /**
   * Returns the member directories of {@code root} that hold package.json or tsconfig.json and
   * another language's build file, which makes each one that language's own project.
   */
  static List<Path> ownLanguageMemberDirectories(final Path root, final Map<?, ?> manifest) {
    return directories(root, manifest, true);
  }

  private static List<Path> directories(
      final Path root, final Map<?, ?> manifest, final boolean otherBuild) {
    final List<String> patterns = patterns(root, manifest);
    if (patterns.isEmpty()) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(root, FileSystemProjectInspection.MAX_SEARCH_DEPTH)) {
      return files
          .filter(Files::isDirectory)
          .filter(member -> !member.toString().contains("node_modules"))
          .filter(member -> hasOtherBuildFile(member) == otherBuild)
          .filter(member -> WorkspaceGlobs.matchesAny(patterns, root, member))
          .filter(NodeWorkspaces::hasProjectFile)
          .sorted()
          .toList();
    } catch (IOException | UncheckedIOException e) {
      return List.of();
    }
  }

  /**
   * Returns whether {@code dir} has another language's build file, such as a Maven POM. Such a
   * package is that language's project, so a Node workspace does not plan it.
   */
  private static boolean hasOtherBuildFile(final Path dir) {
    return FileSystemNodeSources.OTHER_BUILD_FILES.stream()
        .anyMatch(file -> Files.isRegularFile(dir.resolve(file)));
  }

  private static boolean hasProjectFile(final Path dir) {
    return Files.isRegularFile(dir.resolve(NodeProjectInspection.PACKAGE_JSON))
        || Files.isRegularFile(dir.resolve(NodeProjectInspection.TSCONFIG));
  }

  /** Returns the workspace patterns of {@code root}; negations are ignored, not applied. */
  private static List<String> patterns(final Path root, final Map<?, ?> manifest) {
    final List<String> patterns = new ArrayList<>();
    final Object workspaces = manifest.get("workspaces");
    final Object listed =
        workspaces instanceof Map<?, ?> object ? object.get(PACKAGES) : workspaces;
    if (listed instanceof List<?> list) {
      list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(patterns::add);
    }
    patterns.addAll(pnpmPatterns(root));
    return patterns.stream().filter(pattern -> !pattern.startsWith("!")).toList();
  }

  // shortcut: line-based pnpm-workspace.yaml reading, not YAML; upgrade if flow-style lists or
  // anchors show up in real projects.
  private static List<String> pnpmPatterns(final Path root) {
    final Path file = root.resolve(PNPM_WORKSPACE);
    if (!Files.isRegularFile(file)) {
      return List.of();
    }
    final List<String> patterns = new ArrayList<>();
    try {
      boolean inPackages = false;
      for (final String line : Files.readAllLines(file)) {
        final Matcher item = LIST_ITEM.matcher(line);
        if (TOP_LEVEL_KEY.matcher(line).matches()) {
          inPackages = line.startsWith(PACKAGES + ":");
        } else if (inPackages && item.matches()) {
          patterns.add(item.group(1).replaceAll("^['\"]|['\"]$", ""));
        }
      }
    } catch (IOException e) {
      return List.of();
    }
    return patterns;
  }
}
