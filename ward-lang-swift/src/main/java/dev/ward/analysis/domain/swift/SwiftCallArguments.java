package dev.ward.analysis.domain.swift;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads the arguments of a Swift call clause, and names the call that passes them. */
final class SwiftCallArguments {
  private static final String CALL_CLAUSE = "function_call_argument_clause";
  private static final String TRAILING_CLOSURE = "trailing_closure";
  private static final String POSTFIX_OPERATOR = "postfix_operator";
  private static final String LABEL = "label";
  private static final Pattern TYPE_NAME = Pattern.compile("[A-Z]\\w*");

  private SwiftCallArguments() {}

  /**
   * Whether the chain before {@code next} is called: {@code next} opens a call, or passes through
   * an operator such as {@code ?} of a chain that is called.
   */
  static boolean isCallee(final SyntaxNode next, final boolean called) {
    if (next == null) {
      return false;
    }
    final boolean opensCall =
        CALL_CLAUSE.equals(next.kind()) || TRAILING_CLOSURE.equals(next.kind());
    return opensCall || called && POSTFIX_OPERATOR.equals(next.kind());
  }

  /**
   * The expressions that a call clause passes. An expression with a label carries it in its {@code
   * label} field.
   */
  static List<SyntaxNode> of(final SyntaxNode clause) {
    final List<SyntaxNode> values = new ArrayList<>();
    final SyntaxNode list = SwiftFlowSyntax.child(clause, "function_call_argument_list");
    if (list != null) {
      for (final SyntaxNode argument : SwiftFlowSyntax.children(list, "function_call_argument")) {
        values.add(labelled(argument));
      }
    }
    return values;
  }

  private static SyntaxNode labelled(final SyntaxNode argument) {
    final SyntaxNode value = SwiftFlowSyntax.child(argument, "expression");
    final SyntaxNode label = SwiftFlowSyntax.child(argument, "label_identifier");
    if (value == null) {
      return argument;
    }
    return label == null
        ? value
        : new SyntaxNode(
            value.kind(),
            value.span(),
            Map.of(LABEL, SwiftFlowSyntax.text(label)),
            value.children()
        );
  }

  /**
   * The name of a call: its last name, or for a creation with a labelled first argument, the type
   * and the label, as Apple's documentation names it.
   *
   * @param name the last name of the callee
   * @param qualifier the receiver text, empty when the callee is not qualified
   * @param arguments the arguments, as {@link #of(SyntaxNode)} reads them
   * @return the name of the call site
   */
  static String name(final String name, final String qualifier, final List<SyntaxNode> arguments) {
    if (!qualifier.isEmpty() || !TYPE_NAME.matcher(name).matches() || arguments.isEmpty()) {
      return name;
    }
    final String label = arguments.get(0).fields().getOrDefault(LABEL, "");
    return label.isEmpty() ? name : name + "(" + label + ":)";
  }
}
