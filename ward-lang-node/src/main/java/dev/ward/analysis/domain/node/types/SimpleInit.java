package dev.ward.analysis.domain.node.types;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;

/**
 * The type of an initializer that needs no scopes: a literal, {@code new Name(...)}, or an empty
 * array. Anything else is unknown, because the declaration index of another file cannot run the
 * expression typer.
 */
final class SimpleInit {
  private static final String SINGLE_EXPRESSION = "singleExpression";

  private SimpleInit() {}

  /** Returns the type of {@code expression}, keeping literal types when {@code keepLiteral}. */
  static TsType of(final SyntaxNode expression, final boolean keepLiteral) {
    if (expression == null) {
      return TsType.UNKNOWN;
    }
    final SyntaxNode literal = TreeKit.child(expression, "literal");
    if (literal != null) {
      final TsType type = Literals.of(literal).orElse(TsType.UNKNOWN);
      return keepLiteral ? type : Literals.widen(type);
    }
    final List<SyntaxNode> parts = TreeKit.parts(expression);
    if (parts.size() >= 2 && "new".equals(TreeText.text(parts.get(0)))) {
      return newed(parts.get(1));
    }
    return TsType.UNKNOWN;
  }

  private static TsType newed(final SyntaxNode target) {
    final List<SyntaxNode> parts = TreeKit.parts(target);
    if (parts.size() != 1) {
      return TsType.UNKNOWN;
    }
    final SyntaxNode only = parts.get(0);
    if ("identifierName".equals(only.kind())) {
      return TsType.named(TreeText.text(only));
    }
    return SINGLE_EXPRESSION.equals(only.kind()) ? newed(only) : TsType.UNKNOWN;
  }
}
