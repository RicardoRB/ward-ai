package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The context a fragment sits in, from its ancestors, as Biome 2.5.15's {@code noUselessFragments}
 * reads it from the parent of the fragment's wrapper.
 */
final class FragmentContext {

  private static final String FRAGMENT = "jsxFragment";
  private static final String ELEMENT = "jsxElement";
  private static final String EXPRESSION_CHILD = "jsxExpressionContainer";
  private static final String EXPRESSION = "singleExpression";
  private static final String RETURN = "returnStatement";
  private static final String ATTRIBUTE_VALUE = "jsxAttributeValue";
  private static final Set<String> PRESERVING =
      Set.of("variableDeclaration", "arrowFunctionBody", "propertyAssignment");
  private static final Set<String> LOGICAL_OPERATORS = Set.of("&&", "||", "??");
  private static final int WRAPPER_PARENT = 1;
  private static final int OUTER = 2;
  private static final int ATTRIBUTE_CONTAINER = 3;

  /** The contexts that decide whether a fragment is kept or what its children count for. */
  enum Context {
    PRESERVED,
    RETURNED,
    ATTRIBUTE,
    LOGICAL,
    EXPRESSION_CHILD,
    LIST,
    OTHER
  }

  private FragmentContext() {}

  /**
   * The ancestors that decide the context of a visited fragment. A short fragment sits under its
   * own wrapper element, so its ancestors end with the wrapper; an element is its own wrapper, so
   * it is appended to its ancestors.
   */
  static List<SyntaxNode> chainOf(final SyntaxWalk.Visit visit) {
    if (FRAGMENT.equals(visit.node().kind())) {
      return visit.ancestors();
    }
    final List<SyntaxNode> chain = new ArrayList<>(visit.ancestors());
    chain.add(visit.node());
    return chain;
  }

  /**
   * The context of the fragment: its wrapper element's parent decides it. The wrapper is the
   * jsxElement holding the fragment; the expression above it is a singleExpression.
   */
  static Context of(final List<SyntaxNode> ancestors) {
    final int wrapper = ancestors.size() - 1;
    if (wrapper < WRAPPER_PARENT) {
      return Context.OTHER;
    }
    final SyntaxNode parent = ancestors.get(wrapper - WRAPPER_PARENT);
    if (ELEMENT.equals(parent.kind()) || FRAGMENT.equals(parent.kind())) {
      return Context.LIST;
    }
    if (!EXPRESSION.equals(parent.kind()) || wrapper < OUTER) {
      return Context.OTHER;
    }
    return expressionContext(ancestors, wrapper);
  }

  /** The context of a fragment held by an expression, from the expression's own parent. */
  private static Context expressionContext(final List<SyntaxNode> ancestors, final int wrapper) {
    final SyntaxNode outer = ancestors.get(wrapper - OUTER);
    if (EXPRESSION_CHILD.equals(outer.kind())) {
      final boolean inAttribute =
          wrapper >= ATTRIBUTE_CONTAINER
              && ATTRIBUTE_VALUE.equals(ancestors.get(wrapper - ATTRIBUTE_CONTAINER).kind());
      return inAttribute ? Context.ATTRIBUTE : Context.EXPRESSION_CHILD;
    }
    if (EXPRESSION.equals(outer.kind()) && isLogical(outer)) {
      return Context.LOGICAL;
    }
    if (RETURN.equals(outer.kind())) {
      return Context.RETURNED;
    }
    return PRESERVING.contains(outer.kind()) ? Context.PRESERVED : Context.OTHER;
  }

  private static boolean isLogical(final SyntaxNode expression) {
    return expression.children().stream()
        .anyMatch(child -> LOGICAL_OPERATORS.contains(SyntaxWalk.text(child)));
  }
}
