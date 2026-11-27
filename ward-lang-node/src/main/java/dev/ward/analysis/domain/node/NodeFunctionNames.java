package dev.ward.analysis.domain.node;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.Set;

/** The names that the control-flow lowering gives to functions and to members of an object. */
final class NodeFunctionNames {
  private static final Set<String> NAME_PARTS =
      Set.of("identifier", "classElementName", "propertyName");

  private NodeFunctionNames() {}

  /** The own name of a function, else the name of the variable or member that holds it. */
  static String of(final SyntaxNode function, final String hint) {
    for (final SyntaxNode part : NodeFlowSyntax.parts(function)) {
      if (NAME_PARTS.contains(part.kind())) {
        return NodeFlowSyntax.text(part);
      }
    }
    if (hint != null) {
      return hint;
    }
    final Span span = function.span();
    return "<anonymous>@" + span.startLine() + ":" + span.startColumn();
  }

  /** The owner and name of a member of the object that a variable holds, or null. */
  static String member(final SyntaxNode property, final String owner) {
    final SyntaxNode name = NodeFlowSyntax.firstOf(NodeFlowSyntax.parts(property), "propertyName");
    return owner == null || name == null ? null : owner + "#" + NodeFlowSyntax.text(name);
  }

  /** The name of a declared variable, or null. */
  static String variable(final SyntaxNode variable) {
    final SyntaxNode identifier = NodeFlowSyntax.firstIdentifier(variable);
    return identifier == null ? null : NodeFlowSyntax.text(identifier);
  }
}
