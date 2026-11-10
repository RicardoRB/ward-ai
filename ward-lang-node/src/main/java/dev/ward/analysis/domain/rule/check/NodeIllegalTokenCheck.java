package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reports each token of the listed kinds whose text matches the format, like Checkstyle's
 * IllegalTokenText. With no token kinds listed, nothing is reported, as in Checkstyle's defaults.
 */
public final class NodeIllegalTokenCheck implements RuleCheck {

  private static final String COMMENT = "comment";

  private final Set<String> tokens;
  private final Pattern format;
  private final String message;

  /**
   * Creates the check.
   *
   * @param tokens the token kinds to check
   * @param format the pattern a token's text must not match
   * @param ignoreCase whether the format ignores letter case
   * @param message the message of each finding; {@code {value}} is the token's text
   */
  public NodeIllegalTokenCheck(
      final Set<String> tokens,
      final String format,
      final boolean ignoreCase,
      final String message) {
    this.tokens = Set.copyOf(tokens);
    this.format = Pattern.compile(format, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    this.message = message;
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    collect(root, found);
    return List.copyOf(found);
  }

  private void collect(final SyntaxNode node, final List<RuleMatch> found) {
    if (node.children().isEmpty()) {
      illegal(node).ifPresent(found::add);
      return;
    }
    node.children().forEach(child -> collect(child, found));
  }

  private Optional<RuleMatch> illegal(final SyntaxNode token) {
    final String text = token.fields().getOrDefault("text", "");
    if (COMMENT.equals(token.kind())
        || !tokens.contains(token.kind())
        || !format.matcher(text).find()) {
      return Optional.empty();
    }
    return Optional.of(new RuleMatch(token.span(), message.replace("{value}", text)));
  }
}
