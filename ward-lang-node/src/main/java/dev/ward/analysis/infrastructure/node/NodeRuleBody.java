package dev.ward.analysis.infrastructure.node;

import dev.ward.analysis.domain.port.AnalysisFailedException;
import dev.ward.analysis.domain.rule.RuleDefinition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the body of a structural rule. A threshold rule takes {@code max=<positive integer>} or an
 * empty body, which means its default. The naming rule takes an empty body only.
 */
final class NodeRuleBody {

  private static final Pattern MAX = Pattern.compile("max=(\\d+)");

  private NodeRuleBody() {}

  /**
   * Returns the limit in {@code definition}'s body, or {@code defaultLimit} when the body is empty.
   *
   * @throws AnalysisFailedException if the body is neither empty nor a positive {@code max=}
   */
  static int limit(final RuleDefinition definition, final int defaultLimit) {
    final String body = definition.body().strip();
    if (body.isEmpty()) {
      return defaultLimit;
    }
    final Matcher matcher = MAX.matcher(body);
    if (!matcher.matches()) {
      throw malformed(definition);
    }
    return positive(definition, matcher.group(1));
  }

  /**
   * Checks that the body is empty.
   *
   * @throws AnalysisFailedException if the body has any text
   */
  static void requireEmpty(final RuleDefinition definition) {
    if (!definition.body().isBlank()) {
      throw malformed(definition);
    }
  }

  private static int positive(final RuleDefinition definition, final String digits) {
    final int value;
    try {
      value = Integer.parseInt(digits);
    } catch (final NumberFormatException exception) {
      throw malformed(definition);
    }
    if (value < 1) {
      throw malformed(definition);
    }
    return value;
  }

  private static AnalysisFailedException malformed(final RuleDefinition definition) {
    return new AnalysisFailedException(
        "rule "
            + definition.id()
            + " has a body it cannot read; use max=<positive integer> or leave it empty");
  }
}
