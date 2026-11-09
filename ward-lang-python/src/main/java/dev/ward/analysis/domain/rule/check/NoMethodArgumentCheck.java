package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports a method of a class of this module that declares no parameter, like pylint {@code
 * no-method-argument}. A static method, a class method and the constructor hooks are not regular
 * methods and are not reported.
 */
public final class NoMethodArgumentCheck implements RuleCheck {

  private static final Set<String> SCOPES = Set.of("funcdef", "classdef", "lambdef");
  private static final Set<String> EXEMPT_NAMES =
      Set.of("__class_getitem__", "__new__", "__init_subclass__");
  private static final String DEF = "def";
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
          && !EXEMPT_NAMES.contains(functionName(visit.node()))
          && !FunctionDecorators.exemptDecorator(visit.ancestors())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
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

  private static String functionName(final SyntaxNode function) {
    final List<SyntaxNode> children = function.children();
    for (int index = 0; index + 1 < children.size(); index++) {
      if (DEF.equals(SyntaxWalk.text(children.get(index)))) {
        return SyntaxWalk.text(children.get(index + 1));
      }
    }
    return "";
  }
}
