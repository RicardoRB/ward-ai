package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Reports a declaration annotation that is not on the line of the token after it. */
public final class AnnotationOnSameLineCheck implements RuleCheck {

  private static final String ANNOTATION = "annotation";
  private static final Set<String> DECLARATION_HOLDERS =
      Set.of("modifier", "classOrInterfaceModifier");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public AnnotationOnSameLineCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION))) {
      final SyntaxNode annotation = visit.node();
      final SyntaxNode holder = visit.ancestors().get(visit.ancestors().size() - 1);
      final SyntaxNode next = tokens.firstFrom(annotation.span().endOffset());
      if (DECLARATION_HOLDERS.contains(holder.kind())
          && next != null
          && next.span().startLine() != annotation.span().endLine()) {
        matches.add(CheckSupport.at(annotation.span(), message));
      }
    }
    return List.copyOf(matches);
  }
}
