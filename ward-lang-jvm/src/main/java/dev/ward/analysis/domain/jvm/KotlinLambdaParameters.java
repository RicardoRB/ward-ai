package dev.ward.analysis.domain.jvm;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * The writes that give a lambda's parameters their value at the start of the lambda: {@code it}
 * when the lambda names no parameter, else each named one, destructured names included.
 */
final class KotlinLambdaParameters {

  private static final String IT = "it";
  private static final String VARIABLE_DECLARATION = "variableDeclaration";

  private KotlinLambdaParameters() {}

  /**
   * Writes the parameters.
   *
   * @param split the lambda
   * @param part the node the writes are placed in
   * @return one write per parameter, declared where the scope analysis declares it
   */
  static List<VariableAccess> writes(final KotlinSplits.Split split, final Span part) {
    final SyntaxNode list = KotlinFlowSyntax.child(split.node(), "lambdaParameters");
    final List<VariableAccess> writes = new ArrayList<>();
    if (list == null || KotlinFlowSyntax.parts(list).isEmpty()) {
      writes.add(parameter(IT, split.span(), part));
    } else {
      declarations(list)
          .forEach(name -> writes.add(parameter(KotlinFlowSyntax.text(name), name.span(), part)));
    }
    return writes;
  }

  private static VariableAccess parameter(
      final String name, final Span declaration, final Span part) {
    return new VariableAccess(
        name,
        AccessKind.WRITE,
        new Span(
            part.startLine(),
            part.startColumn(),
            part.startLine(),
            part.startColumn(),
            part.startOffset(),
            part.startOffset()),
        declaration);
  }

  private static List<SyntaxNode> declarations(final SyntaxNode node) {
    final List<SyntaxNode> names = new ArrayList<>();
    for (final SyntaxNode child : KotlinFlowSyntax.parts(node)) {
      if (VARIABLE_DECLARATION.equals(child.kind())) {
        names.add(KotlinScopeWalk.simple(child));
      } else {
        names.addAll(declarations(child));
      }
    }
    return names;
  }
}
