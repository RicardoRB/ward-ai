package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/** Reads the argument lists of Java calls and class creations, for flow sites. */
final class JavaCallArguments {

  private static final String ARGUMENTS = "arguments";
  private static final String EXPRESSION_LIST = "expressionList";
  private static final String EXPRESSION = "expression";

  private JavaCallArguments() {}

  /** Returns the expressions of the argument list of a call, or of a class creator rest. */
  static List<SyntaxNode> of(final SyntaxNode call) {
    final SyntaxNode arguments = JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(call), ARGUMENTS);
    final SyntaxNode list =
        arguments == null
            ? null
            : JavaFlowSyntax.firstOf(JavaFlowSyntax.parts(arguments), EXPRESSION_LIST);
    return list == null ? List.of() : JavaFlowSyntax.allOf(JavaFlowSyntax.parts(list), EXPRESSION);
  }

  /** Returns the spans of the given nodes, in order. */
  static List<Span> spans(final List<SyntaxNode> nodes) {
    return nodes.stream().map(SyntaxNode::span).toList();
  }
}
