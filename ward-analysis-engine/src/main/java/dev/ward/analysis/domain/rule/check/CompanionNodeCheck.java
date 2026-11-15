package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports a node that has no companion in the same body. In each node of the filter's kinds (the
 * body), a descendant of kind {@code node} whose text matches the {@code match} pattern needs
 * another descendant whose text matches the companion pattern, in which {@code {1}} stands for the
 * quoted text of the first group of the match. For example, {@code addCookie(c)} needs {@code
 * c.setSecure(true)} in the same method. Both patterns match the whole text, with no whitespace as
 * everywhere in node text. A body is searched as a whole, so the check does not follow the order of
 * the statements or the calls between methods.
 */
public final class CompanionNodeCheck implements UnitCheck {

  private static final String GROUP = "{1}";

  private final NodeFilter filter;
  private final String nodeKind;
  private final Pattern match;
  private final String companion;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the bodies to search
   * @param nodeKind the kind of the nodes whose text is tested
   * @param match the pattern of the node that needs a companion; it has one group
   * @param companion the companion pattern, with {@code {1}} for the first group of the match
   * @param message the finding message
   */
  public CompanionNodeCheck(
      final NodeFilter filter,
      final String nodeKind,
      final Pattern match,
      final String companion,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.nodeKind = Objects.requireNonNull(nodeKind, "nodeKind");
    this.match = Objects.requireNonNull(match, "match");
    this.companion = Objects.requireNonNull(companion, "companion");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return filter.select(root, file).stream()
        .flatMap(body -> missing(body.node()).stream())
        .toList();
  }

  private List<RuleMatch> missing(final SyntaxNode body) {
    final List<SyntaxNode> nodes = new ArrayList<>();
    collect(body, nodes);
    return nodes.stream()
        .filter(node -> lacksCompanion(node, nodes))
        .map(node -> CheckSupport.at(node.span(), message))
        .toList();
  }

  private boolean lacksCompanion(final SyntaxNode node, final List<SyntaxNode> nodes) {
    final Matcher matcher = match.matcher(SyntaxWalk.text(node));
    return matcher.matches() && !hasCompanion(nodes, matcher.group(1));
  }

  private boolean hasCompanion(final List<SyntaxNode> nodes, final String captured) {
    final Pattern wanted = Pattern.compile(companion.replace(GROUP, Pattern.quote(captured)));
    return nodes.stream().anyMatch(node -> wanted.matcher(SyntaxWalk.text(node)).matches());
  }

  private void collect(final SyntaxNode node, final List<SyntaxNode> into) {
    if (nodeKind.equals(node.kind())) {
      into.add(node);
    }
    for (final SyntaxNode child : node.children()) {
      collect(child, into);
    }
  }
}
