package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fails each declaration that passes the filter when no comment sits immediately before its
 * wrapper. A declaration carrying the exempt annotation is never checked.
 */
public final class PrecedingCommentCheck implements RuleCheck {

  private static final String AT = "@";

  private final DeclarationFilter filter;
  private final Optional<String> exemptAnnotation;
  private final String message;

  /**
   * Creates the check.
   *
   * @param filter the declarations to check
   * @param exemptAnnotation an annotation name whose declarations are never checked, if any
   * @param message the message reported for an undocumented declaration
   */
  public PrecedingCommentCheck(
      final DeclarationFilter filter,
      final Optional<String> exemptAnnotation,
      final String message) {
    this.filter = Objects.requireNonNull(filter, "filter");
    this.exemptAnnotation = Objects.requireNonNull(exemptAnnotation, "exemptAnnotation");
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, filter.kinds())) {
      final Declaration declaration = Declaration.of(visit, filter.modifierKinds());
      final List<String> modifiers = declaration.modifiers();
      if (CheckSupport.admits(modifiers, filter.required(), filter.excluded())
          && !hasExemptAnnotation(modifiers)
          && !documented(declaration)) {
        matches.add(new RuleMatch(visit.node().span(), CheckSupport.fill(message, "", "")));
      }
    }
    return List.copyOf(matches);
  }

  private boolean hasExemptAnnotation(final List<String> modifiers) {
    return exemptAnnotation.isPresent()
        && indexOfAnnotation(modifiers, exemptAnnotation.get()) >= 0;
  }

  /** Finds {@code @name} as two consecutive tokens; returns its index or -1. */
  private static int indexOfAnnotation(final List<String> modifiers, final String name) {
    for (int index = 0; index + 1 < modifiers.size(); index++) {
      if (AT.equals(modifiers.get(index)) && name.equals(modifiers.get(index + 1))) {
        return index;
      }
    }
    return -1;
  }

  private static boolean documented(final Declaration declaration) {
    return declaration
        .previousSibling()
        .map(sibling -> SyntaxWalk.COMMENT_KIND.equals(sibling.kind()))
        .orElse(false);
  }
}
