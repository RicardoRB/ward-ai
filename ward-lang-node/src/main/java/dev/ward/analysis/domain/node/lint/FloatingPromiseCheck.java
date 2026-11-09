package dev.ward.analysis.domain.node.lint;

import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.rule.RuleMatch;
import dev.ward.analysis.domain.types.UnitTypeFacts;
import java.util.ArrayList;
import java.util.List;

/** Reports an expression statement that calls a promise and neither awaits nor handles it. */
public final class FloatingPromiseCheck extends TypedCheck {

  private static final String STATEMENT = "expressionStatement";
  private static final String VOID = "void";
  private static final String CATCH = "catch";
  private static final String THEN = "then";
  private static final String ARGUMENTS = "arguments";

  private final boolean ignoreVoid;
  private final boolean ignoreIife;
  private final String message;

  /** Creates the check with the rule's options and message. */
  public FloatingPromiseCheck(
      final boolean ignoreVoid, final boolean ignoreIife, final String message) {
    super();
    this.ignoreVoid = ignoreVoid;
    this.ignoreIife = ignoreIife;
    this.message = message;
  }

  @Override
  protected List<RuleMatch> find(final SyntaxNode root, final UnitTypeFacts types) {
    final List<RuleMatch> out = new ArrayList<>();
    for (final SyntaxNode statement : SyntaxText.nodesOf(root, STATEMENT)) {
      final SyntaxNode call = callOf(statement);
      if (call != null && isFloating(call, types)) {
        out.add(new RuleMatch(call.span(), message));
      }
    }
    return out;
  }

  /** Returns the call of a statement that is a single expression, or null. */
  private SyntaxNode callOf(final SyntaxNode statement) {
    final List<SyntaxNode> parts = statement.children();
    if (parts.isEmpty()
        || !SEQUENCE.equals(parts.get(0).kind())
        || parts.get(0).children().size() != 1) {
      return null;
    }
    SyntaxNode expression = parts.get(0).children().get(0);
    if (expression.children().size() == 2
        && SyntaxText.isToken(expression.children().get(0), VOID)) {
      if (ignoreVoid) {
        return null;
      }
      expression = expression.children().get(1);
    }
    return expression.children().size() == 2
            && ARGUMENTS.equals(expression.children().get(1).kind())
        ? expression
        : null;
  }

  private boolean isFloating(final SyntaxNode call, final UnitTypeFacts types) {
    if (!typeOf(types, call).filter(TypedCheck::isPromise).isPresent()) {
      return false;
    }
    final SyntaxNode callee = call.children().get(0);
    final String arguments = SyntaxText.of(call.children().get(1));
    if (ignoreIife && SyntaxText.of(callee).startsWith("(")) {
      return false;
    }
    return !handled(callee, arguments);
  }

  /** A promise handled by {@code .catch(...)} or by {@code .then} with a rejection handler. */
  private static boolean handled(final SyntaxNode callee, final String arguments) {
    final List<SyntaxNode> parts = callee.children();
    if (parts.size() != SyntaxText.INFIX_PARTS) {
      return false;
    }
    final String member = SyntaxText.of(parts.get(2));
    return CATCH.equals(member) || THEN.equals(member) && arguments.contains(",");
  }
}
