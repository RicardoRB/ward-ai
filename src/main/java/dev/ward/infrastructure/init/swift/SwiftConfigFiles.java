package dev.ward.infrastructure.init.swift;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.project.FileSystemSwiftToolConfigs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The tool configs init writes in a Swift project root: {@code .swiftlint.yml}, and {@code
 * .swift-format} with the indentation of the project's sources. A config the project keeps is never
 * changed. Strict over a project's own {@code .swiftlint.yml} writes {@code
 * .ward/swiftlint-strict.yml} instead, which the SwiftLint command passes after the user's file.
 */
public final class SwiftConfigFiles {
  static final String SWIFTLINT = ".swiftlint.yml";
  static final String STRICT_SWIFTLINT = ".ward/swiftlint-strict.yml";
  static final String SWIFT_FORMAT = ".swift-format";
  private static final String MARKER = "# managed-by: ward";
  private static final int SOURCE_LIMIT = 200;
  private static final int SEARCH_DEPTH = 8;
  private static final int DEFAULT_SPACES = 4;
  private static final int NARROW_SPACES = 2;
  private static final Set<String> EXCLUDED = Set.of(".build", "Pods", "Carthage", "DerivedData");
  private static final String SWIFTLINT_CONFIG =
      MARKER
          + "\n"
          + "excluded:\n"
          + "  - .build\n"
          + "  - .ward\n"
          + "  - Pods\n"
          + "  - Carthage\n"
          + "  - DerivedData\n";
  // trailing_comma is disabled: swift-format owns formatting and keeps the trailing comma that
  // SwiftLint's default rule rejects, so under --strict the two would make every package fail.
  private static final String STRICT_RULES =
      "disabled_rules:\n"
          + "  - trailing_comma\n"
          + "opt_in_rules:\n"
          + "  - force_unwrapping\n"
          + "  - implicitly_unwrapped_optional\n"
          + "  - fatal_error_message\n"
          + "  - empty_count\n"
          + "  - first_where\n"
          + "  - contains_over_first_not_nil\n"
          + "cyclomatic_complexity:\n"
          + "  warning: 10\n";

  private SwiftConfigFiles() {}

  /** Returns each config file of a Swift project, in the order init writes them. */
  public static List<ConfigTarget> targets(final Project project, final QualityPreset preset) {
    final Path root = project.root();
    final FileSystemSwiftToolConfigs configs = new FileSystemSwiftToolConfigs();
    final boolean strict = preset == QualityPreset.STRICT;
    final boolean ownSwiftLint = configs.userConfig(root, SWIFTLINT).isPresent();
    final List<ConfigTarget> targets = new ArrayList<>();
    targets.add(
        ownSwiftLint
            ? ConfigTarget.keep(root.resolve(SWIFTLINT), "kept: your .swiftlint.yml")
            : ConfigTarget.write(root.resolve(SWIFTLINT), swiftLintConfig(strict)));
    strictLayer(root, strict && ownSwiftLint).ifPresent(targets::add);
    targets.add(
        configs.userConfig(root, SWIFT_FORMAT).isPresent()
            ? ConfigTarget.keep(root.resolve(SWIFT_FORMAT), "kept: your .swift-format")
            : ConfigTarget.write(root.resolve(SWIFT_FORMAT), swiftFormat(indentation(root))));
    return List.copyOf(targets);
  }

  /** The {@code .swiftlint.yml} Ward writes: strict adds the opt-in rules and complexity. */
  private static String swiftLintConfig(final boolean strict) {
    return strict ? SWIFTLINT_CONFIG + STRICT_RULES : SWIFTLINT_CONFIG;
  }

  /**
   * The strict layer over a user's SwiftLint config: written when {@code wanted}, and otherwise
   * removed when a previous strict run left it behind. Empty when neither applies.
   */
  private static Optional<ConfigTarget> strictLayer(final Path root, final boolean wanted) {
    final Path file = root.resolve(STRICT_SWIFTLINT);
    if (wanted) {
      return Optional.of(ConfigTarget.write(file, MARKER + "\n" + STRICT_RULES));
    }
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    return Optional.of(
        new ConfigTarget(
            file, ConfigTarget.Disposition.REMOVE, null, "removed: strict layer no longer used"));
  }

  /** The {@code .swift-format} JSON for {@code indentation}: {@code tabs} or a number of spaces. */
  static String swiftFormat(final String indentation) {
    final String value = "tabs".equals(indentation) ? "\"tabs\": 1" : "\"spaces\": " + indentation;
    return "{\"version\": 1, \"indentation\": {" + value + "}}\n";
  }

  /**
   * Returns {@code tabs} when most indented source lines start with a tab, else the more common
   * indent step of 2 or 4 spaces, defaulting to 4 when the project has no indented Swift.
   */
  static String indentation(final Path root) {
    final IndentTally tally = new IndentTally();
    swiftFiles(root).forEach(file -> tally.add(read(file)));
    if (tally.tabLines > 0 && tally.tabLines > tally.spaceLines) {
      return "tabs";
    }
    return tally.twoSteps > tally.fourSteps
        ? String.valueOf(NARROW_SPACES)
        : String.valueOf(DEFAULT_SPACES);
  }

  /** Counts how indented source lines begin: with a tab, or with a 2- or 4-space step. */
  private static final class IndentTally {
    private int tabLines;
    private int spaceLines;
    private int twoSteps;
    private int fourSteps;

    /** Counts the indented lines of one file, measuring each step from the line before. */
    void add(final List<String> lines) {
      int previous = 0;
      for (final String line : lines) {
        if (line.isBlank()) {
          continue;
        }
        if (line.startsWith("\t")) {
          tabLines++;
          continue;
        }
        final int width = leadingSpaces(line);
        if (width > 0) {
          spaceLines++;
        }
        countStep(width - previous);
        previous = width;
      }
    }

    private void countStep(final int step) {
      if (step == NARROW_SPACES) {
        twoSteps++;
      } else if (step == DEFAULT_SPACES) {
        fourSteps++;
      }
    }
  }

  private static int leadingSpaces(final String line) {
    int count = 0;
    while (count < line.length() && line.charAt(count) == ' ') {
      count++;
    }
    return count;
  }

  /** The Swift files under {@code root}, skipping hidden and dependency directories. */
  private static List<Path> swiftFiles(final Path root) {
    try (Stream<Path> files = Files.walk(root, SEARCH_DEPTH)) {
      return files
          .filter(path -> path.toString().endsWith(".swift") && Files.isRegularFile(path))
          .filter(path -> !excluded(root.relativize(path)))
          .limit(SOURCE_LIMIT)
          .toList();
    } catch (IOException e) {
      return List.of();
    }
  }

  private static boolean excluded(final Path relative) {
    for (final Path part : relative) {
      final String name = part.toString();
      if (name.startsWith(".") || EXCLUDED.contains(name)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> read(final Path file) {
    try {
      return Files.readAllLines(file);
    } catch (IOException e) {
      return List.of();
    }
  }
}
