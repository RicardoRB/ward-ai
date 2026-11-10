package dev.ward.application.rules;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.rule.RulePreset;
import dev.ward.domain.config.WardException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The filters of {@code ward rules list}.
 *
 * @param preset the preset that selects the rule, or {@code null}; {@link RulePreset#NONE} matches
 *     the rules that no preset selects
 * @param language the language the rule covers, or {@code null}
 * @param category the category of the rule, or {@code null}
 * @param selected whether to keep only the rules {@code ward-rules.toml} selects
 */
public record RuleFilter(
    RulePreset preset, AnalysisLanguage language, String category, boolean selected) {

  private static final String NONE = "none";

  /**
   * Parses the filter values as the command line gives them.
   *
   * @throws WardException if a value is not a known preset or language
   */
  public static RuleFilter parse(
      final String preset, final String language, final String category, final boolean selected) {
    return new RuleFilter(
        preset == null ? null : presetOf(preset),
        language == null ? null : languageOf(language),
        category,
        selected);
  }

  private static RulePreset presetOf(final String key) {
    if (NONE.equals(key)) {
      return RulePreset.NONE;
    }
    return RulePreset.byKey(key)
        .orElseThrow(
            () ->
                new WardException(
                    "--preset must be one of none, "
                        + Arrays.stream(RulePreset.values())
                            .map(RulePreset::key)
                            .sorted()
                            .collect(Collectors.joining(", "))
                        + "; got \""
                        + key
                        + "\""));
  }

  private static AnalysisLanguage languageOf(final String name) {
    try {
      return AnalysisLanguage.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      throw new WardException(
          "--language must be one of "
              + Arrays.stream(AnalysisLanguage.values())
                  .map(language -> language.name().toLowerCase(Locale.ROOT))
                  .collect(Collectors.joining(", "))
              + "; got \""
              + name
              + "\"",
          exception);
    }
  }
}
