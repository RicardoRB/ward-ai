package dev.ward.analysis.domain.rule;

import dev.ward.analysis.domain.ir.AnalysisLanguage;
import dev.ward.analysis.domain.ir.AnalysisSeverity;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One analysis rule as declared in data. Only the {@link RuleMatcher} reads the {@code body}.
 *
 * <p>A rule covers one language, and its id is {@code ward:<language>/<rule>}. A rule that covers
 * several languages, such as the cross-language {@code ward:architecture/layers}, has the id prefix
 * {@code architecture} instead.
 *
 * @param id the stable id, {@code ward:<language>/<rule>}, or {@code ward:architecture/<rule>} for
 *     a rule covering several languages
 * @param languages the languages the rule applies to, never empty
 * @param capability the one capability the rule requires from its frontend
 * @param severity the severity of the rule's findings
 * @param body the declarative rule text, in the format the matcher understands
 */
public record RuleDefinition(
    String id,
    Set<AnalysisLanguage> languages,
    RuleCapability capability,
    AnalysisSeverity severity,
    String body) {

  private static final int SINGLE_LANGUAGE = 1;

  /** The id prefix of a rule that covers several languages. */
  public static final String CROSS_LANGUAGE = "architecture";

  private static final Pattern ID = Pattern.compile("ward:([a-z]+)/([a-z][a-z0-9-]*)");

  /**
   * Creates a rule that covers one language.
   *
   * @throws NullPointerException if a field is missing
   * @throws IllegalArgumentException if the id is not {@code ward:<language>/<rule>} for this
   *     language
   */
  public RuleDefinition(
      final String id,
      final AnalysisLanguage language,
      final RuleCapability capability,
      final AnalysisSeverity severity,
      final String body) {
    this(id, Set.of(Objects.requireNonNull(language, "language")), capability, severity, body);
  }

  /**
   * Validates the required fields and the id, and copies the languages.
   *
   * @throws NullPointerException if a field is missing
   * @throws IllegalArgumentException if no language is given, or the id does not name them
   */
  public RuleDefinition {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(languages, "languages");
    Objects.requireNonNull(capability, "capability");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(body, "body");
    if (languages.isEmpty()) {
      throw new IllegalArgumentException("rule '" + id + "' must name at least one language");
    }
    final String prefix = prefixFor(languages);
    final Matcher matcher = ID.matcher(id);
    if (!matcher.matches() || !matcher.group(1).equals(prefix)) {
      throw new IllegalArgumentException(
          "rule id must be ward:" + prefix + "/<rule>, found '" + id + "'");
    }
    languages = Set.copyOf(languages);
  }

  /** Returns whether this rule applies to {@code language}. */
  public boolean covers(final AnalysisLanguage language) {
    return languages.contains(language);
  }

  private static String prefixFor(final Set<AnalysisLanguage> languages) {
    if (languages.size() > SINGLE_LANGUAGE) {
      return CROSS_LANGUAGE;
    }
    return languages.iterator().next().name().toLowerCase(Locale.ROOT);
  }
}
