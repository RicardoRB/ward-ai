package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reports an {@code __init__} method of a class whose own body yields, like Pylint's {@code
 * init-is-generator}. A yield in a function, lambda or class nested in the method belongs to that
 * scope and is not reported.
 */
public final class InitGeneratorCheck implements RuleCheck {
  private static final String CLASSDEF = "classdef";

  private static final String FUNCTION = "funcdef";
  private static final String INIT = "__init__";
  private static final String NAME = "NAME";
  private static final String YIELD = "yield_expr";
  private static final Set<String> FUNCTIONS = Set.of(FUNCTION);
  private static final Set<String> SCOPES =
      Set.of("funcdef", "async_funcdef", "lambdef", "classdef");

  private final String message;

  /**
   * Creates the check.
   *
   * @param message the message of each finding
   */
  public InitGeneratorCheck(final String message) {
    this.message = Objects.requireNonNull(message, "message");
  }

  @Override
  public List<RuleMatch> matches(final SyntaxNode root) {
    final List<RuleMatch> found = new ArrayList<>();
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(root, FUNCTIONS)) {
      if (isClassInit(visit.node(), visit.ancestors()) && yieldsInOwnBody(visit.node())) {
        found.add(new RuleMatch(visit.node().span(), message));
      }
    }
    return List.copyOf(found);
  }

  /** A function named {@code __init__} whose nearest enclosing frame is a class. */
  private static boolean isClassInit(final SyntaxNode function, final List<SyntaxNode> ancestors) {
    final boolean named =
        function.children().stream()
            .filter(child -> NAME.equals(child.kind()) || "name".equals(child.kind()))
            .findFirst()
            .map(SyntaxWalk::text)
            .filter(INIT::equals)
            .isPresent();
    if (!named) {
      return false;
    }
    for (int index = ancestors.size() - 1; index >= 0; index--) {
      final String kind = ancestors.get(index).kind();
      if (CLASSDEF.equals(kind)) {
        return true;
      }
      if (SCOPES.contains(kind)) {
        return false;
      }
    }
    return false;
  }

  /** Whether a yield in the function is not inside a function, lambda or class nested in it. */
  private static boolean yieldsInOwnBody(final SyntaxNode function) {
    for (final SyntaxWalk.Visit visit : SyntaxWalk.visits(function, Set.of(YIELD))) {
      // The first ancestor is the method itself.
      if (visit.ancestors().stream()
          .skip(1)
          .noneMatch(ancestor -> SCOPES.contains(ancestor.kind()))) {
        return true;
      }
    }
    return false;
  }
}
