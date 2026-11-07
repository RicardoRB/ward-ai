package dev.ward.application.gate;

import dev.ward.analysis.domain.rule.RuleDefinition;
import dev.ward.analysis.domain.rule.RuleOptions;
import dev.ward.domain.project.Project;
import java.util.Map;

/**
 * The Java release a project targets. A rule that depends on it declares the option {@link
 * #OPTION}, whose default is the release Ward detects; {@link #UNKNOWN} means no release was found,
 * and such a rule reports nothing.
 */
final class JavaRelease {

  /** The rule option that carries the release. */
  static final String OPTION = "java-release";

  /** The release when none can be detected: no finding depends on it. */
  static final int UNKNOWN = 0;

  private static final String LEGACY_PREFIX = "1.";

  private JavaRelease() {}

  /**
   * Returns the release of {@code project}, read from its language version, or {@link #UNKNOWN}.
   */
  static int of(final Project project) {
    final String version = project.languageVersion();
    if (version == null || version.isEmpty()) {
      return UNKNOWN;
    }
    final String digits =
        version.startsWith(LEGACY_PREFIX) ? version.substring(LEGACY_PREFIX.length()) : version;
    try {
      final int release = Integer.parseInt(digits);
      return release > UNKNOWN ? release : UNKNOWN;
    } catch (final NumberFormatException exception) {
      return UNKNOWN;
    }
  }

  /**
   * Returns {@code rule} with its {@link #OPTION} set to {@code release}; a rule without the option
   * is returned unchanged.
   */
  static RuleDefinition withDefault(final RuleDefinition rule, final int release) {
    return rule.option(OPTION).isEmpty()
        ? rule
        : RuleOptions.apply(rule, Map.<String, Object>of(OPTION, Long.valueOf(release)));
  }
}
