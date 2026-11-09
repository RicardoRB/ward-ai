package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The body of each preset rule file, in the format its tool loads. */
final class PresetRuleFileContent {
  /** The options the strict preset sets on the {@code ward:java/hidden-field} rule. */
  private static final String HIDDEN_FIELD =
      "\n[rules.\"ward:java/hidden-field\"]\nignore-constructor-parameter = true\n"
          + "ignore-setter = true\n";

  private PresetRuleFileContent() {}

  /**
   * The rule selection of the {@code ward} gate: the Ward rules of the preset, for the JVM source
   * languages the project has, Java before Kotlin, then Python when the project has it, then Swift.
   * A project without a JVM language selects Java, as it always has.
   */
  static String wardRules(final QualityPreset preset, final Set<Language> languages) {
    final QualityPreset chosen = preset == null ? QualityPreset.STANDARD : preset;
    final List<String> ids = jvmLanguageIds(languages);
    if (languages.contains(Language.PYTHON)) {
      ids.add("python");
    }
    ids.add("swift");
    return "version = 1\n\n[select]\npreset = \""
        + chosen.id()
        + "\"\nlanguages = "
        + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(", ", "[", "]"))
        + "\n"
        + (chosen == QualityPreset.STRICT && ids.contains("java") ? HIDDEN_FIELD : "");
  }

  private static List<String> jvmLanguageIds(final Set<Language> languages) {
    final List<String> ids = new ArrayList<>();
    if (languages.contains(Language.JAVA)) {
      ids.add("java");
    }
    if (languages.contains(Language.KOTLIN)) {
      ids.add("kotlin");
    }
    if (ids.isEmpty()) {
      ids.add("java");
    }
    return ids;
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
