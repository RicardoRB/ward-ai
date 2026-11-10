package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a method says about nullness without a flow analysis: which names it declares nullable, and
 * which of those it checks or reassigns anywhere, so that a check can stay silent about them.
 */
final class NullFlow {

  private static final Pattern NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
  private static final Set<String> CHECKS = Set.of("requireNonNull", "nonNull", "isNull");
  private static final String EXPRESSION = "expression";
  private static final String METHOD_CALL = "methodCall";

  /** The assignment operator. */
  static final String ASSIGN = "=";

  private static final String ARGUMENTS = "arguments";

  /** A call: its name, receiver text ({@code ""} when unqualified) and arguments. */
  record Call(String name, String receiver, List<SyntaxNode> args, SyntaxNode node) {}

  private NullFlow() {}

  /** Tells whether a declaration is never null: annotated so, or unannotated in a marked type. */
  static boolean nonNull(
      final NullModel.Notes notes, final boolean marked, final boolean primitive) {
    return !primitive && !notes.nullable() && (marked || notes.nonNull());
  }

  /** Returns the call that {@code expression} is, if it is one. */
  static Optional<Call> call(final SyntaxNode expression) {
    final List<SyntaxNode> parts = expression.children();
    if (!EXPRESSION.equals(expression.kind()) || parts.isEmpty()) {
      return Optional.empty();
    }
    final SyntaxNode last = parts.get(parts.size() - 1);
    final boolean qualified =
        parts.size() == NullSyntax.BINARY && ".".equals(SyntaxWalk.text(parts.get(1)));
    if (!METHOD_CALL.equals(last.kind()) || parts.size() != 1 && !qualified) {
      return Optional.empty();
    }
    final String receiver = qualified ? SyntaxWalk.text(parts.get(0)) : "";
    final String name = NullSyntax.first(last, "identifier").map(SyntaxWalk::text).orElse("");
    return Optional.of(new Call(name, receiver, arguments(last), expression));
  }

  /** Returns the argument expressions of a method call or an object creation. */
  static List<SyntaxNode> arguments(final SyntaxNode holder) {
    return SyntaxWalk.firstDescendant(holder, ARGUMENTS, Set.of())
        .flatMap(args -> NullSyntax.first(args, "expressionList"))
        .map(list -> list.children().stream().filter(c -> EXPRESSION.equals(c.kind())).toList())
        .orElse(List.of());
  }

  /** Returns the names that a method checks against null, tests with instanceof or assigns. */
  static Set<String> guarded(final NullModel.Method method) {
    final Set<String> names = new HashSet<>();
    for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
      final List<SyntaxNode> parts = expression.children();
      final String operator = NullSyntax.operator(expression);
      if ("==".equals(operator) || "!=".equals(operator)) {
        names.add(NullSyntax.name(NullSyntax.isNull(parts.get(2)) ? parts.get(0) : parts.get(2)));
      } else if (ASSIGN.equals(operator)) {
        names.add(NullSyntax.name(parts.get(0)));
      } else if (parts.size() > 1 && "instanceof".equals(SyntaxWalk.text(parts.get(1)))) {
        names.add(NullSyntax.name(parts.get(0)));
      }
      call(expression)
          .filter(call -> CHECKS.contains(call.name()) && !call.args().isEmpty())
          .ifPresent(call -> names.add(NullSyntax.name(call.args().get(0))));
    }
    return names;
  }

  /** Returns the names of the parameters and fields that are nullable and never checked. */
  static Set<String> nullable(final NullModel.Type type, final NullModel.Method method) {
    final Set<String> names = new HashSet<>();
    type.fields().stream()
        .filter(field -> field.notes().nullable())
        .forEach(field -> names.add(field.name()));
    method.params().forEach(param -> names.remove(param.name()));
    method.params().stream()
        .filter(param -> param.notes().nullable())
        .forEach(param -> names.add(param.name()));
    names.removeAll(guarded(method));
    return names;
  }

  /** Returns the names of the methods of {@code type} that are annotated to return null. */
  static Set<String> nullableCalls(final NullModel.Type type) {
    final Set<String> names = new HashSet<>();
    type.methods().stream()
        .filter(method -> method.notes().nullable())
        .forEach(method -> names.add(method.name()));
    type.methods().stream()
        .filter(method -> !method.notes().nullable())
        .forEach(method -> names.remove(method.name()));
    return names;
  }

  /** Tells whether {@code expression} is a name in {@code names} or a call of a nullable method. */
  static boolean nullable(
      final SyntaxNode expression, final Set<String> names, final Set<String> calls) {
    final String text = NullSyntax.name(expression);
    if (NAME.matcher(text).matches()) {
      return names.contains(text);
    }
    return call(expression)
        .filter(call -> call.receiver().isEmpty() || "this".equals(call.receiver()))
        .map(call -> calls.contains(call.name()))
        .orElse(false);
  }

  /** Tells whether {@code expression} is the literal null or {@link #nullable} says it may be. */
  static boolean nullish(
      final SyntaxNode expression, final Set<String> names, final Set<String> calls) {
    return NullSyntax.isNull(expression) || nullable(expression, names, calls);
  }

  /** Returns the return statements of {@code body} that return a value, as their expressions. */
  static List<SyntaxNode> returned(final SyntaxNode body) {
    final List<SyntaxNode> out = new ArrayList<>();
    for (final SyntaxNode statement : NullSyntax.scan(body, Set.of("statement"))) {
      final List<SyntaxNode> parts = statement.children();
      if (parts.size() > 1
          && "return".equals(SyntaxWalk.text(parts.get(0)))
          && EXPRESSION.equals(parts.get(1).kind())) {
        out.add(parts.get(1));
      }
    }
    return out;
  }

  /**
   * Tells whether {@code body} assigns the field {@code name}, as {@code name =} or {@code
   * this.name =}.
   */
  static boolean assigns(final SyntaxNode body, final String name, final Set<String> shadowed) {
    if (body == null) {
      return false;
    }
    for (final SyntaxNode expression : NullSyntax.expressions(body)) {
      if (ASSIGN.equals(NullSyntax.operator(expression))) {
        final String target = SyntaxWalk.text(expression.children().get(0));
        if (target.equals("this." + name) || target.equals(name) && !shadowed.contains(name)) {
          return true;
        }
      }
    }
    return false;
  }
}
