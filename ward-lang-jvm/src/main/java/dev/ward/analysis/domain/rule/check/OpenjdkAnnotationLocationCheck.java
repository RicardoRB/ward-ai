package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Checks the OpenJDK placement of declaration annotations. Annotations either all sit on one line
 * with the declaration, or each has its own line: an annotation that shares its line with the token
 * after it is reported unless every annotation and the declaration share one line.
 */
public final class OpenjdkAnnotationLocationCheck implements RuleCheck {

  private static final Set<String> MODIFIER_KINDS = Set.of("modifier", "classOrInterfaceModifier");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message
   */
  public OpenjdkAnnotationLocationCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final SyntaxNode node : SyntaxWalk.descendants(root)) {
      if (!MODIFIER_KINDS.contains(node.kind()) && !node.children().isEmpty()) {
        checkDeclaration(node, tokens, matches);
      }
    }
    return List.copyOf(matches);
  }

  private void checkDeclaration(
      final SyntaxNode holder, final TokenSequence tokens, final List<RuleMatch> matches) {
    final List<SyntaxNode> annotations =
        holder.children().stream().filter(child -> SyntaxWalk.text(child).startsWith("@")).toList();
    if (annotations.isEmpty()) {
      return;
    }
    final SyntaxNode last = annotations.get(annotations.size() - 1);
    final SyntaxNode target = tokens.firstFrom(last.span().endOffset());
    final boolean oneLine =
        annotations.get(0).span().startLine() == last.span().endLine()
            && (target == null || target.span().startLine() == last.span().endLine());
    if (oneLine) {
      return;
    }
    for (final SyntaxNode annotation : annotations) {
      final SyntaxNode next = tokens.firstFrom(annotation.span().endOffset());
      if (next != null && next.span().startLine() == annotation.span().endLine()) {
        matches.add(CheckSupport.at(annotation.span(), message));
      }
    }
  }
}
