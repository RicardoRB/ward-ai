package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One analysis rule as declared in data. Only the {@link RuleMatcher} reads the {@code body}.
 *
 * @param id the stable id, {@code ward:<language>/<rule>}, where {@code <language>} is the
 *     lowercase language name
 * @param language the language the rule applies to
 * @param capability the one capability the rule requires from its frontend
 * @param severity the severity of the rule's findings
 * @param body the declarative rule text, in the format the matcher understands
 */
public record RuleDefinition(
    String id,
    AnalysisLanguage language,
    RuleCapability capability,
    AnalysisSeverity severity,
    String body) {

  private static final Pattern ID = Pattern.compile("ward:([a-z]+)/([a-z][a-z0-9-]*)");

  /**
   * Validates the required fields and the id.
   *
   * @throws NullPointerException if a field is missing
   * @throws IllegalArgumentException if the id is not {@code ward:<language>/<rule>} for this
   *     language
   */
  public RuleDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(capability, "capability");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(body, "body");
    final String languageName = language.name().toLowerCase(Locale.ROOT);
    final Matcher matcher = ID.matcher(id);
    if (!matcher.matches() || !matcher.group(1).equals(languageName)) {
      throw new IllegalArgumentException(
          "rule id must be ward:" + languageName + "/<rule>, found '" + id + "'");
    }
  }
}
