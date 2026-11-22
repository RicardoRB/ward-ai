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
 * after it is reported unless the annotations and the declaration form one chain: each starts on the
 * line where the previous one ends, and the declaration starts on the line of the last.
 */
public final class OpenjdkAnnotationLocationCheck implements RuleCheck {
  private static final Set<String> MODIFIER_KINDS = Set.of(
      "modifier",
      "classOrInterfaceModifier",
      "variableModifier"
  );
  private static final Set<String> ANNOTATION_KINDS =
      Set.of("annotation", "modifier", "classOrInterfaceModifier", "variableModifier");

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

  /**
   * Whether each annotation starts on the line where the one before it ends, as {@code ward format}
   * writes a parameter whose annotation spans several lines: {@code ) @B String x}.
   */
  private static boolean startsWhereThePreviousEnds(final List<SyntaxNode> annotations) {
    for (int i = 1; i < annotations.size(); i++) {
      if (annotations.get(i).span().startLine() != annotations.get(i - 1).span().endLine()) {
        return false;
      }
    }
    return true;
  }

  private void checkDeclaration(
      final SyntaxNode holder,
      final TokenSequence tokens,
      final List<RuleMatch> matches
  ) {
    final List<SyntaxNode> annotations =
        holder
            .children()
            .stream()
            .filter(child -> ANNOTATION_KINDS.contains(child.kind()))
            .filter(child -> SyntaxWalk.text(child).startsWith("@"))
            .toList();
    if (annotations.isEmpty()) {
      return;
    }
    final SyntaxNode last = annotations.get(annotations.size() - 1);
    final SyntaxNode target = tokens.firstFrom(last.span().endOffset());
    final boolean chained =
        target == null || target.span().startLine() == last.span().endLine();
    if (chained && startsWhereThePreviousEnds(annotations)) {
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
