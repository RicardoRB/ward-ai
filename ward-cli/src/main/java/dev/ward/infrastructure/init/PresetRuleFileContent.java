package dev.ward.infrastructure.init;

import dev.ward.domain.buildpolicy.BuildRule;
import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The body of each preset rule file, in the format its tool loads. */
final class PresetRuleFileContent {
  /** The options the strict preset sets on the {@code ward:java/hidden-field} rule. */
  private static final String HIDDEN_FIELD =
      "\n[rules.java.hidden-field]\nignore-constructor-parameter = true\n"
          + "ignore-setter = true\n";

  /** The native build rules the strict preset selects; the {@code ward} gate ignores them. */
  private static final String BUILD_RULES =
      Stream.of(BuildRule.values())
          .filter(BuildRule::strict)
          .map(rule -> "\"build." + rule.ruleName() + "\"")
          .collect(Collectors.joining(", ", "include = [", "]\n"));

  /**
   * The native rules that replaced Maven Enforcer's {@code dependencyConvergence} and {@code
   * requireUpperBoundDeps}; the strict preset selects them for Maven projects, which report the
   * versions requested before mediation. Gradle already picks the highest requested version.
   */
  private static final String MAVEN_RULES =
      Stream.of(BuildRule.DEPENDENCY_CONVERGENCE, BuildRule.REQUIRE_UPPER_BOUND)
          .map(rule -> "\"build." + rule.ruleName() + "\"")
          .collect(Collectors.joining(", "));

  private PresetRuleFileContent() {}

  /**
   * The rule selection of the {@code ward} gate: the Ward rules of the preset, for the JVM source
   * languages the project has, Java before Kotlin, then Python when the project has it, then Swift.
   * A project without a JVM language selects Java, as it always has.
   */
  static String wardRules(
      final QualityPreset preset,
      final List<String> languages,
      final boolean buildPolicy,
      final boolean maven) {
    final QualityPreset chosen = preset == null ? QualityPreset.STANDARD : preset;
    return "version = 1\n\n[select]\npreset = \""
        + chosen.id()
        + "\"\nlanguages = "
        + languages.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ", "[", "]"))
        + "\n"
        + (buildPolicy && chosen == QualityPreset.STRICT ? buildRules(languages, maven) : "")
        + (chosen == QualityPreset.STRICT && languages.contains("java") ? HIDDEN_FIELD : "");
  }

  private static String buildRules(final List<String> languages, final boolean maven) {
    final boolean jvm = languages.contains("java") || languages.contains("kotlin");
    return maven && jvm
        ? BUILD_RULES.substring(0, BUILD_RULES.length() - "]\n".length())
            + ", "
            + MAVEN_RULES
            + "]\n"
        : BUILD_RULES;
  }
}
