package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports each labeled statement whose label no {@code break} or {@code continue} inside it names.
 * A labeled statement is a {@code statement} whose first child is the label identifier and whose
 * last child is the labeled statement itself.
 */
public final class UnusedLabelCheck implements UnitCheck {

  private static final String STATEMENT = "statement";
  private static final String IDENTIFIER = "identifier";
  private static final Set<String> JUMPS = Set.of("'break'", "'continue'");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the label
   */
  public UnusedLabelCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> check(final SyntaxNode root, final SourceFile file) {
    return SyntaxWalk.visits(root, Set.of(STATEMENT)).stream()
        .map(SyntaxWalk.Visit::node)
        .filter(UnusedLabelCheck::isLabeled)
        .filter(labeled -> !jumpsTo(labeled, label(labeled)))
        .map(
            labeled ->
                CheckSupport.at(labeled.span(), CheckSupport.fill(message, label(labeled), "")))
        .toList();
  }

  private static boolean isLabeled(final SyntaxNode node) {
    final List<SyntaxNode> children = node.children();
    return children.size() >= 2
        && IDENTIFIER.equals(children.get(0).kind())
        && STATEMENT.equals(children.get(children.size() - 1).kind());
  }

  private static String label(final SyntaxNode labeled) {
    return SyntaxWalk.text(labeled.children().get(0));
  }

  private static boolean jumpsTo(final SyntaxNode labeled, final String label) {
    return SyntaxWalk.descendants(labeled).stream().anyMatch(node -> isJumpTo(node, label));
  }

  private static boolean isJumpTo(final SyntaxNode node, final String label) {
    final boolean jump = node.children().stream().anyMatch(c -> JUMPS.contains(c.kind()));
    return jump
        && node.children().stream()
            .anyMatch(c -> IDENTIFIER.equals(c.kind()) && label.equals(SyntaxWalk.text(c)));
  }
}
