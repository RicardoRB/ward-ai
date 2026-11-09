package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SourceUnit;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.FlowView;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;

/**
 * Reports a non-empty Kotlin file whose text does not end with a line feed, as detekt's {@code
 * NewLineAtEndOfFile} does. The finding sits at the start of the file. The syntax tree drops
 * whitespace, so the check reads the unit text.
 */
public final class KotlinNewLineAtEndOfFileCheck implements RuleCheck {

  private static final char LINE_FEED = '\n';

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of the finding
   */
  public KotlinNewLineAtEndOfFileCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  /** Always empty: the check needs the file text, which only the unit overload has. */
  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    return List.of();
  }

  @Override
  public List<RuleMatch> matches(
      final SyntaxNode root, final FlowView flow, final SourceUnit unit) {
    final String text = unit.text();
    if (text.isEmpty() || text.charAt(text.length() - 1) == LINE_FEED) {
      return List.of();
    }
    return List.of(new RuleMatch(root.span(), message));
  }
}
