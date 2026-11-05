package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Reads the names and operators of Java syntax nodes for the access finder. */
final class JavaSyntaxNames {

  private static final String PRIMARY = "primary";
  private static final String IDENTIFIER = "identifier";
  private static final String VARIABLE_DECLARATOR_ID = "variableDeclaratorId";
  private static final String ASSIGN = "=";
  private static final String INCREMENT = "++";
  private static final String DECREMENT = "--";
  private static final Set<String> COMPOUND_OPERATORS =
      Set.of("+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=");

  private JavaSyntaxNames() {}

  /** Returns the identifier a declarator id holds, or null. */
  static SyntaxNode identifierOf(final SyntaxNode holder) {
    return holder == null ? null : JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(holder), IDENTIFIER);
  }

  /** Returns the name a resource declares: its direct identifier or its declarator id's. */
  static SyntaxNode nameOfResource(final List<SyntaxNode> parts) {
    final SyntaxNode direct = JavaFlowSyntax.firstOf(parts, IDENTIFIER);
    return direct != null
        ? direct
        : identifierOf(JavaFlowSyntax.firstOf(parts, VARIABLE_DECLARATOR_ID));
  }

  /** Reports whether a part is an assignment operator, simple or compound. */
  static boolean isAssignment(final SyntaxNode operator) {
    final String text = JavaFlowSyntax.text(operator);
    return ASSIGN.equals(text) || COMPOUND_OPERATORS.contains(text);
  }

  /** Reports whether two parts are an operand and an increment or decrement operator. */
  static boolean isIncrement(final List<SyntaxNode> parts) {
    return isOperator(parts.get(0)) || isOperator(parts.get(1));
  }

  /** Reports whether a part is an increment or decrement operator. */
  static boolean isOperator(final SyntaxNode part) {
    final String text = JavaFlowSyntax.text(part);
    return INCREMENT.equals(text) || DECREMENT.equals(text);
  }

  /**
   * Returns the identifier of a plain variable reference, or null. A plain reference is an
   * identifier alone in a primary, so a method name, a member name and a type name are not one.
   */
  static SyntaxNode simpleVariable(final SyntaxNode node) {
    final SyntaxNode value = JavaFlowSyntax.unwrap(node);
    if (!PRIMARY.equals(value.kind())) {
      return null;
    }
    final List<SyntaxNode> parts = JavaFlowSyntax.parts(value);
    return parts.size() == 1 && IDENTIFIER.equals(parts.get(0).kind()) ? parts.get(0) : null;
  }

  /** Returns the plain variable references in a subtree, in source order. */
  static List<SyntaxNode> namesIn(final SyntaxNode node) {
    final List<SyntaxNode> names = new ArrayList<>();
    collectNames(node, names);
    return names;
  }

  private static void collectNames(final SyntaxNode node, final List<SyntaxNode> names) {
    final SyntaxNode name = simpleVariable(node);
    if (name != null) {
      names.add(name);
      return;
    }
    for (final SyntaxNode child : node.children()) {
      collectNames(child, names);
    }
  }
}
