package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.regex.Pattern;

/** Works out what value an expression has: null, non-null, a known variable's value, or unknown. */
final class FlowNullValues {

  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Pattern PLAIN = Pattern.compile("\"[^\"]*\"|-?\\d[\\w.]*|true|false|'.*'");
  private static final String NULL = "null";
  private static final int TERNARY = 5;
  private static final int CAST = 4;
  private static final int ELSE_BRANCH = 4;
  private static final int CAST_OPERAND = 3;

  private FlowNullValues() {}

  /** Returns the value of {@code expression} in {@code state}. */
  static FlowNullValue of(
      final SyntaxNode expression, final FlowNullState state, final FlowNullScope scope) {
    final String text = SyntaxWalk.text(expression);
    final List<SyntaxNode> parts = expression.children();
    if (NULL.equals(text)) {
      return FlowNullValue.NULL;
    }
    if (PLAIN.matcher(text).matches() || "this".equals(text) || isCreation(parts)) {
      return FlowNullValue.NON_NULL;
    }
    if (NAME.matcher(text).matches()) {
      return scope
          .variable(expression)
          .map(decl -> state.get(decl).value())
          .orElse(FlowNullValue.UNKNOWN);
    }
    if (parts.size() == TERNARY) {
      return of(parts.get(2), state, scope).join(of(parts.get(ELSE_BRANCH), state, scope));
    }
    if (parts.size() == CAST && "(".equals(SyntaxWalk.text(parts.get(0)))) {
      return of(parts.get(CAST_OPERAND), state, scope);
    }
    return call(expression, scope);
  }

  private static boolean isCreation(final List<SyntaxNode> parts) {
    return !parts.isEmpty() && "new".equals(SyntaxWalk.text(parts.get(0)));
  }

  /** The value a call of a method of this file returns, from its annotation. */
  private static FlowNullValue call(final SyntaxNode expression, final FlowNullScope scope) {
    return FlowNullCalls.of(expression)
        .flatMap(call -> scope.index().method(call.name(), call.args().size()))
        .map(method -> fromReturn(method.returns()))
        .orElse(FlowNullValue.UNKNOWN);
  }

  private static FlowNullValue fromReturn(final FlowNullQual qual) {
    return switch (qual) {
      case MAYBE -> FlowNullValue.NULLABLE;
      case NEVER -> FlowNullValue.NEVER;
      case ALWAYS -> FlowNullValue.NON_NULL;
      default -> FlowNullValue.UNKNOWN;
    };
  }

  /** Tells whether {@code expression} is a plain variable or field name. */
  static boolean isName(final SyntaxNode expression) {
    return NAME.matcher(SyntaxWalk.text(expression)).matches();
  }
}
