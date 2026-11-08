package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.AccessKind;
import dev.ward.analysis.domain.ir.BasicBlock;
import dev.ward.analysis.domain.ir.Span;
import dev.ward.analysis.domain.ir.SyntaxNode;
import dev.ward.analysis.domain.ir.VariableAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads the expressions of one block as the null data-flow sees them: which local variable an
 * expression names, whether it is null or never null, and what a null test says about a variable.
 */
final class NullOperands {

  private static final String EXPRESSION = "expression";
  private static final String NULL = "null";
  private static final String EQUALS = "==";
  private static final int BINARY = 3;
  private static final int TERNARY = 5;
  private static final int CREATION = 2;
  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Pattern NULL_LITERAL =
      Pattern.compile("(?:\\([A-Za-z0-9_.<>\\[\\]]*\\))*\\(*null\\)*");
  private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
  private static final java.util.Set<String> NON_NULL_WORDS =
      java.util.Set.of("this", "true", "false");

  private final Map<Span, Span> locals = new HashMap<>();

  NullOperands(final BasicBlock block) {
    for (final VariableAccess access : block.accesses()) {
      if (access.kind() != AccessKind.CAPTURE) {
        locals.put(access.span(), access.declaration());
      }
    }
  }

  /** Returns the declaration of the local variable written or read at {@code at}, or null. */
  Span local(final Span at) {
    return locals.get(at);
  }

  /** Returns the declaration of the local variable that {@code node} names, or null. */
  Span variableOf(final SyntaxNode node) {
    return EXPRESSION.equals(node.kind()) && NAME.matcher(SyntaxWalk.text(node)).matches()
        ? locals.get(node.span())
        : null;
  }

  NullEvent.Operand operand(final SyntaxNode node) {
    final String text = SyntaxWalk.text(node);
    final Span variable = variableOf(node);
    if (NULL_LITERAL.matcher(text).matches()) {
      return new NullEvent.Operand(null, true, false, node);
    }
    if (variable != null) {
      return new NullEvent.Operand(variable, false, false, node);
    }
    return new NullEvent.Operand(null, false, neverNull(node, text), node);
  }

  private static boolean neverNull(final SyntaxNode node, final String text) {
    final List<SyntaxNode> parts = node.children();
    return NON_NULL_WORDS.contains(text)
        || STRING.matcher(text).matches()
        || parts.size() == CREATION && "new".equals(SyntaxWalk.text(parts.get(0)));
  }

  /** Returns the event for a dereference of {@code receiver}, if it is a local variable. */
  Optional<NullEvent> dereference(final SyntaxNode receiver, final String method) {
    return Optional.ofNullable(variableOf(receiver))
        .map(variable -> NullEvent.deref(variable, receiver, method));
  }

  /** Returns the arguments of a call with what is known about each. */
  List<NullEvent.Arg> arguments(final List<SyntaxNode> args) {
    return args.stream().map(arg -> new NullEvent.Arg(operand(arg), arg)).toList();
  }

  /** Returns the name of the field an assignment target names, or null. */
  static String fieldName(final SyntaxNode target) {
    final String text = SyntaxWalk.text(target);
    final String bare = text.startsWith("this.") ? text.substring("this.".length()) : text;
    return NAME.matcher(bare).matches() ? bare : null;
  }

  static String methodName(final SyntaxNode invoked) {
    return NullSyntax.first(invoked, "identifier").map(SyntaxWalk::text).orElse("");
  }

  /** Tells whether the expression evaluates only its first operand for sure. */
  static boolean isGuarded(final String operator, final List<SyntaxNode> parts) {
    return "&&".equals(operator) || "||".equals(operator) || isTernary(parts);
  }

  static boolean isTernary(final List<SyntaxNode> parts) {
    return parts.size() == TERNARY && "?".equals(SyntaxWalk.text(parts.get(1)));
  }

  /** Reads {@code variable ==|!= null}: the variable and what each branch holds. */
  Optional<NullEvents.Test> nullTest(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    final String operator = NullSyntax.operator(node);
    if (!EQUALS.equals(operator) && !"!=".equals(operator)) {
      return Optional.empty();
    }
    final boolean nullLeft = NULL.equals(SyntaxWalk.text(parts.get(0)));
    final Span variable = variableOf(parts.get(nullLeft ? 2 : 0));
    if (variable == null || !nullLeft && !NULL.equals(SyntaxWalk.text(parts.get(2)))) {
      return Optional.empty();
    }
    final boolean equal = EQUALS.equals(operator);
    final Nullity same = equal ? Nullity.NULL : Nullity.NONNULL;
    final Nullity other = equal ? Nullity.NONNULL : Nullity.NULL;
    return Optional.of(new NullEvents.Test(variable, same, other));
  }

  Optional<NullEvents.Test> instanceTest(final SyntaxNode node) {
    final List<SyntaxNode> parts = node.children();
    if (!EXPRESSION.equals(node.kind())
        || parts.size() < BINARY
        || !"instanceof".equals(SyntaxWalk.text(parts.get(1)))) {
      return Optional.empty();
    }
    return Optional.ofNullable(variableOf(parts.get(0)))
        .map(variable -> new NullEvents.Test(variable, Nullity.NONNULL, null));
  }
}
