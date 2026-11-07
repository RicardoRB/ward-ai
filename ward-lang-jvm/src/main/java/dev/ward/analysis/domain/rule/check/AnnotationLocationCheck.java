package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a declaration annotation that shares its line with the declaration that follows it. A
 * single annotation without arguments may share the line unless {@code
 * allowSamelineSingleParameterlessAnnotation} is off, and an annotation with arguments may share it
 * only when {@code allowSamelineParameterizedAnnotation} holds.
 */
public final class AnnotationLocationCheck implements RuleCheck {

  private static final String ANNOTATION = "annotation";
  private static final String ARGUMENTS = "annotationFieldValues";
  private static final Set<String> DECLARATION_HOLDERS =
      Set.of("modifier", "classOrInterfaceModifier");

  private final boolean allowSingleParameterless;
  private final boolean allowParameterized;
  private final String message;

  /**
   * Creates the check.
   *
   * @param allowSingleParameterless whether a parameterless annotation may share the line
   * @param allowParameterized whether an annotation with arguments may share the line
   * @param message the message
   */
  public AnnotationLocationCheck(
      final boolean allowSingleParameterless,
      final boolean allowParameterized,
      final String message) {
    this.allowSingleParameterless = allowSingleParameterless;
    this.allowParameterized = allowParameterized;
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    final TokenSequence tokens = TokenSequence.of(root);
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of(ANNOTATION))) {
      final SyntaxNode annotation = visit.node();
      final SyntaxNode holder = visit.ancestors().get(visit.ancestors().size() - 1);
      if (DECLARATION_HOLDERS.contains(holder.kind()) && isMisplaced(tokens, annotation)) {
        matches.add(CheckSupport.at(annotation.span(), message));
      }
    }
    return List.copyOf(matches);
  }

  private boolean isMisplaced(final TokenSequence tokens, final SyntaxNode annotation) {
    final SyntaxNode next = nextToken(tokens, annotation);
    if (next == null || next.span().startLine() != annotation.span().endLine()) {
      return false;
    }
    final boolean parameterized =
        annotation.children().stream().anyMatch(child -> ARGUMENTS.equals(child.kind()));
    return parameterized ? !allowParameterized : !allowSingleParameterless;
  }

  private static SyntaxNode nextToken(final TokenSequence tokens, final SyntaxNode annotation) {
    return tokens.all().stream()
        .map(TokenSequence.Token::node)
        .filter(token -> token.span().startOffset() >= annotation.span().endOffset())
        .findFirst()
        .orElse(null);
  }
}
