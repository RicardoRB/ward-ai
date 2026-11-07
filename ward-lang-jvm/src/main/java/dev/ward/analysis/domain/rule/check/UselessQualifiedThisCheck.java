package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports {@code Type.this} inside {@code Type} itself, as PMD's UselessQualifiedThis does. The
 * qualifier is needed from an anonymous class or a nested type, so only the nearest enclosing
 * declaration is compared.
 */
public final class UselessQualifiedThisCheck implements RuleCheck {

  private static final String THIS = "'this'";
  private static final String ANONYMOUS = "classCreatorRest";
  private static final int QUALIFIED_CHILDREN = 3;

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message; {@code {value}} is the qualifier
   */
  public UselessQualifiedThisCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> matches = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("expression"))) {
      final SyntaxNode node = visit.node();
      final String qualifier = qualifier(node);
      if (!qualifier.isEmpty() && qualifier.equals(enclosing(visit.ancestors()))) {
        matches.add(CheckSupport.at(node.span(), CheckSupport.fill(message, qualifier, "")));
      }
    }
    return List.copyOf(matches);
  }

  private static String qualifier(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    final boolean qualified =
        parts.size() == QUALIFIED_CHILDREN
            && THIS.equals(parts.get(2).kind())
            && "expression".equals(parts.get(0).kind());
    return qualified ? SyntaxWalk.text(parts.get(0)) : "";
  }

  private static String enclosing(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final SyntaxNode ancestor = ancestors.get(index);
      if (ANONYMOUS.equals(ancestor.kind())) {
        return "";
      }
      if (JavaNodes.TYPE_KINDS.contains(ancestor.kind())) {
        return JavaNodes.name(ancestor);
      }
    }
    return "";
  }
}
