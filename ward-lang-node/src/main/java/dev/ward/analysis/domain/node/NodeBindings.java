package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finds the names that binding patterns bind, and the names a body refers to. */
final class NodeBindings {

  private static final String LEAF = "Identifier";
  private static final String PROPERTY_ASSIGNMENT = "propertyAssignment";
  private static final String PROPERTY_NAME = "propertyName";
  private static final String COLON = ":";
  private static final String DOT = ".";
  private static final String OPTIONAL_DOT = "?.";
  private static final Set<String> MEMBER_TOKENS = Set.of(DOT, OPTIONAL_DOT);
  private static final Set<String> BODIES = Set.of("functionBody", "arrowFunctionBody", "block");
  private static final Set<String> PARAMETER_LISTS =
      Set.of("parameterList", "formalParameterList", "arrowFunctionParameters");

  private NodeBindings() {}

  /**
   * Returns the identifier tokens a binding pattern binds, in source order. A keyed property binds
   * only its value; a type annotation binds nothing.
   */
  static List<SyntaxNode> bindings(final SyntaxNode node) {
    final List<SyntaxNode> names = new ArrayList<>();
    collectBindings(node, names);
    return names;
  }

  /** Returns the parameter list of a function, outside its body, or null. */
  static SyntaxNode parameterList(final SyntaxNode node) {
    if (PARAMETER_LISTS.contains(node.kind())) {
      return node;
    }
    for (final SyntaxNode child : node.children()) {
      if (!BODIES.contains(child.kind())) {
        final SyntaxNode found = parameterList(child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /** Returns the variable names a body refers to, skipping property keys and member names. */
  static List<SyntaxNode> namesIn(final SyntaxNode node) {
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(node, names);
    return names;
  }

  private static void collectBindings(final SyntaxNode node, final List<SyntaxNode> names) {
    if (NodeSyntax.SKIPPED.contains(node.kind())) {
      return;
    }
    if (LEAF.equals(node.kind())) {
      names.add(node);
      return;
    }
    if (PROPERTY_ASSIGNMENT.equals(node.kind())
        && NodeSyntax.hasToken(NodeFlowSyntax.parts(node), COLON)) {
      final List<SyntaxNode> parts = NodeFlowSyntax.parts(node);
      collectBindings(parts.get(parts.size() - 1), names);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectBindings(child, names);
    }
  }

  private static void collectNames(final SyntaxNode node, final List<SyntaxNode> names) {
    if (NodeSyntax.SKIPPED.contains(node.kind()) || PROPERTY_NAME.equals(node.kind())) {
      return;
    }
    if (NodeSyntax.isName(node.kind())) {
      if (NodeSyntax.leafOf(node) != null) {
        names.add(NodeSyntax.leafOf(node));
      }
      return;
    }
    boolean memberName = false;
    for (final SyntaxNode child : node.children()) {
      if (memberName) {
        memberName = false;
      } else if (MEMBER_TOKENS.contains(NodeFlowSyntax.text(child))) {
        memberName = true;
      } else {
        collectNames(child, names);
      }
    }
  }
}
