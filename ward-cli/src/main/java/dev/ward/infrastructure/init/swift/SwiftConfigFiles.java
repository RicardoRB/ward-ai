package dev.ward.infrastructure.init.swift;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.init.ConfigTarget;
import dev.ward.infrastructure.project.swift.FileSystemSwiftToolConfigs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The tool configs init writes in a Swift project root: {@code .swiftlint.yml}. A config the project
 * keeps is never changed. Strict over a project's own {@code .swiftlint.yml} writes {@code
 * .ward/swiftlint-strict.yml} instead, which the SwiftLint command passes after the user's file.
 */
public final class SwiftConfigFiles {
  static final String SWIFTLINT = ".swiftlint.yml";
  static final String STRICT_SWIFTLINT = ".ward/swiftlint-strict.yml";
  private static final String MARKER = "# managed-by: ward";
  private static final String SWIFTLINT_CONFIG =
      MARKER
          + "\n"
          + "excluded:\n"
          + "  - .build\n"
          + "  - .ward\n"
          + "  - Pods\n"
          + "  - Carthage\n"
          + "  - DerivedData\n";
  // trailing_comma is disabled: Ward's formatter keeps the trailing comma that SwiftLint's default
  // rule rejects, so under --strict the two would make every package fail.
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
}
