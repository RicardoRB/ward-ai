package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Gives the parameters of function expressions passed as arguments the types the callee expects.
 */
final class ParamContext {
  private static final String ARROW = "arrowFunctionDeclaration";
  private static final String ARROW_PARAMETERS = "arrowFunctionParameters";

  private ParamContext() {}

  /** Gives the parameters of the function expression {@code node} the types of {@code expected}. */
  static void contextualize(
      final SyntaxNode node, final TsType expected, final BiConsumer<SyntaxNode, TsType> sink) {
    final SyntaxNode function = functionOf(node);
    if (!(expected instanceof TsType.Fn fn) || function == null) {
      return;
    }
    final List<SyntaxNode> params = parameters(function);
    for (int index = 0; index < params.size() && index < fn.sig().params().size(); index++) {
      sink.accept(params.get(index), fn.sig().params().get(index).type());
    }
  }

  private static List<SyntaxNode> parameters(final SyntaxNode function) {
    final SyntaxNode arrow = TreeKit.child(function, ARROW_PARAMETERS);
    final List<SyntaxNode> params = new ArrayList<>();
    if (arrow != null && TreeKit.child(arrow, "propertyName") != null) {
      params.add(arrow);
      return params;
    }
    final SyntaxNode list = TreeKit.find(function, "formalParameterList");
    if (list != null) {
      params.addAll(TreeKit.children(list, "formalParameterArg"));
    }
    return params;
  }

  private static SyntaxNode functionOf(final SyntaxNode node) {
    if (ARROW.equals(node.kind())) {
      return node;
    }
    final List<SyntaxNode> parts = TreeKit.parts(node);
    final boolean wrapper = parts.size() == 1 || "anonymousFunction".equals(node.kind());
    return wrapper && !parts.isEmpty() ? functionOf(parts.get(0)) : null;
  }
}
