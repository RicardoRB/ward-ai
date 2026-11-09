package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method of a class of this module that declares no parameter, like pylint {@code
 * no-method-argument}. Only a static method is exempt: a class method or a constructor hook such as
 * {@code __new__} without parameters is reported too.
 */
public final class NoMethodArgumentCheck implements RuleCheck {

  private static final Set<String> SCOPES = Set.of("funcdef", "classdef", "lambdef");
  private static final Set<String> STATIC_DECORATORS =
      Set.of("staticmethod", "builtins.staticmethod");
  private static final String EMPTY_PARAMETERS = "()";

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public NoMethodArgumentCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, Set.of("funcdef"))) {
      if (directMethod(visit.ancestors())
          && noParameters(visit.node())
          && !staticMethod(visit.ancestors())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** True when the function is decorated as a static method; astroid types nothing else as one. */
  private static boolean staticMethod(final List<SyntaxNode> ancestors) {
    for (int index = Math.max(0, ancestors.size() - 2); index < ancestors.size(); index++) {
      final SyntaxNode node = ancestors.get(index);
      if (SyntaxKinds.DECORATED.equals(node.kind())) {
        return FunctionDecorators.decoratorLines(SyntaxWalk.text(node)).stream()
            .anyMatch(STATIC_DECORATORS::contains);
      }
    }
    return false;
  }

  /** True when the nearest scope above the function is a class. */
  private static boolean directMethod(final List<SyntaxNode> ancestors) {
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (SCOPES.contains(kind)) {
        return SyntaxKinds.CLASSDEF.equals(kind);
      }
    }
    return false;
  }

  private static boolean noParameters(final SyntaxNode function) {
    for (final SyntaxNode child : function.children()) {
      if (SyntaxKinds.PARAMETERS.equals(child.kind())) {
        return EMPTY_PARAMETERS.equals(SyntaxWalk.text(child).strip());
      }
    }
    return false;
  }
}
