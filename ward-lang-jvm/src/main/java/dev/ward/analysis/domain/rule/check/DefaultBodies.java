package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.List;

/** Tells whether the body of a default method only calls the abstract method of its interface. */
final class DefaultBodies {

  private DefaultBodies() {}

  static boolean preserves(
      final SyntaxNode method,
      final String own,
      final InterfaceSam.Sam sam,
      final UnitTypeFacts types) {
    final List<SyntaxNode> statements =
        SyntaxWalk.descendants(method).stream()
            .filter(node -> "blockStatement".equals(node.kind()))
            .filter(node -> depth(method, node) == 1)
            .toList();
    final boolean boxedVoid =
        NullSyntax.first(
                NullSyntax.first(method, "interfaceCommonBodyDeclaration").orElse(method),
                "typeTypeOrVoid")
            .map(type -> SyntaxWalk.text(type).endsWith("Void"))
            .orElse(false);
    if (boxedVoid) {
      return statements.size() == 2
          && callsSam(statements.get(0), own, sam, types)
          && "returnnull;".equals(SyntaxWalk.text(statements.get(1)));
    }
    return statements.size() == 1 && callsSam(statements.get(0), own, sam, types);
  }

  /** Counts the blocks between the method and the node, so only its own statements are taken. */
  private static int depth(final SyntaxNode method, final SyntaxNode statement) {
    return (int)
        SyntaxWalk.descendants(method).stream()
            .filter(node -> "block".equals(node.kind()))
            .filter(
                block ->
                    block.span().startOffset() <= statement.span().startOffset()
                        && block.span().endOffset() >= statement.span().endOffset())
            .count();
  }

  private static boolean callsSam(
      final SyntaxNode statement,
      final String own,
      final InterfaceSam.Sam sam,
      final UnitTypeFacts types) {
    return SyntaxWalk.descendants(statement).stream()
        .filter(node -> "expression".equals(node.kind()))
        .findFirst()
        .flatMap(expression -> types.callTarget(expression.span()))
        .filter(
            target ->
                target.name().equals(sam.name())
                    && target.parameters().size() == sam.arity()
                    && simple(target.declaringType().name()).equals(own))
        .isPresent();
  }

  private static String simple(final String name) {
    return name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1);
  }
}
