package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.stream.Collectors;

/** The body of each preset rule file, in the format its tool loads. */
final class PresetRuleFileContent {
  /** The options the strict preset sets on the {@code ward:java/hidden-field} rule. */
  private static final String HIDDEN_FIELD =
      "\n[rules.java.hidden-field]\nignore-constructor-parameter = true\n"
          + "ignore-setter = true\n";

  private PresetRuleFileContent() {}

  /**
   * The rule selection of the {@code ward} gate: the Ward rules of the preset, for the JVM source
   * languages the project has, Java before Kotlin, then Python when the project has it, then Swift.
   * A project without a JVM language selects Java, as it always has.
   */
  static String wardRules(final QualityPreset preset, final List<String> languages) {
    final QualityPreset chosen = preset == null ? QualityPreset.STANDARD : preset;
    return "version = 1\n\n[select]\npreset = \""
        + chosen.id()
        + "\"\nlanguages = "
        + languages.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ", "[", "]"))
        + "\n"
        + (chosen == QualityPreset.STRICT && languages.contains("java") ? HIDDEN_FIELD : "");
  }

  /** {@code ExternalRules} reads the rules from an {@code <enforcer><rules>} document. */
  static String enforcer(final QualityPreset preset) {
    return "<enforcer>\n<rules>\n"
        + preset.rules().enforcerRules().stream()
            .map(
                rule ->
                    "requireMavenVersion".equals(rule)
                        ? "  <requireMavenVersion><version>[3.9,)</version></requireMavenVersion>\n"
                        : "  <" + rule + "/>\n")
            .reduce("", String::concat)
        + "</rules>\n</enforcer>\n";
  }
}
