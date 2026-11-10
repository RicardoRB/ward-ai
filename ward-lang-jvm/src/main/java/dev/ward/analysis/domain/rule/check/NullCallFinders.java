package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules about a call: what it passes, what it dereferences, and what it casts. */
final class NullCallFinders {

  private static final String EXPRESSION = "expression";
  private static final String LOCAL = "localVariableDeclaration";
  private static final String CAST_NON_NULL = "castToNonNull";
  private static final String NEW = "new";

  /** The children of {@code a[i]}: the array, {@code [}, the index and {@code ]}. */
  private static final int INDEXED = 4;

  private NullCallFinders() {}

  /** A null, or a nullable name, passed for a parameter that must not be null. */
  static List<NullHit> passNullable(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          final Set<String> names = NullFlow.nullable(type, method);
          final Set<String> calls = NullFlow.nullableCalls(type);
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            final Optional<NullCallees.Callee> callee = NullCallees.resolve(unit, type, expression);
            callee.ifPresent(
                target ->
                    NullCallees.passes(target, NullFlow.arguments(expression), names, calls, hits));
          }
        });
  }

  /** A member access on a name or a call that may be null, which the method never checks. */
  static List<NullHit> dereferenceNullable(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          final Set<String> names = NullFlow.nullable(type, method);
          final Set<String> calls = NullFlow.nullableCalls(type);
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            final List<SyntaxNode> parts = expression.children();
            if (parts.size() == NullSyntax.BINARY
                && ".".equals(SyntaxWalk.text(parts.get(1)))
                && EXPRESSION.equals(parts.get(0).kind())
                && NullFlow.nullable(parts.get(0), names, calls)) {
              hits.add(NullHit.at(parts.get(0), SyntaxWalk.text(parts.get(0))));
            }
          }
        });
  }

  /** A null stored in an array whose elements must not be null. */
  static List<NullHit> assignNullableToNonnullArray(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          if (!type.marked()) {
            return;
          }
          final Map<String, Boolean> arrays = arrays(type, method);
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            if (!"=".equals(NullSyntax.operator(expression))) {
              continue;
            }
            final SyntaxNode value = expression.children().get(2);
            final List<SyntaxNode> target = expression.children().get(0).children();
            if (NullSyntax.isNull(value)
                && target.size() == INDEXED
                && "[".equals(SyntaxWalk.text(target.get(1)))
                && arrays.getOrDefault(SyntaxWalk.text(target.get(0)), false)) {
              hits.add(NullHit.at(value, SyntaxWalk.text(target.get(0))));
            }
          }
        });
  }

  /** Maps each array or other variable name to whether it is an array of non-null elements. */
  private static Map<String, Boolean> arrays(
      final NullModel.Type type, final NullModel.Method method) {
    final Map<String, Boolean> arrays = new HashMap<>();
    type.fields()
        .forEach(field -> arrays.put(field.name(), field.array() && !field.notes().nullable()));
    method.params().forEach(param -> arrays.put(param.name(), isArrayParam(param)));
    for (final SyntaxNode local : NullSyntax.scan(method.body(), Set.of(LOCAL))) {
      final Optional<SyntaxNode> declared = NullSyntax.first(local, "typeType");
      final boolean array = declared.isPresent() && NullSyntax.isArray(declared.get());
      final boolean nullable =
          NullSyntax.notes(NullSyntax.annotations(local)).nullable()
              || declared.isPresent()
                  && NullSyntax.notes(NullSyntax.annotations(declared.get())).nullable();
      SyntaxWalk.visits(local, Set.of("variableDeclaratorId"))
          .forEach(visit -> arrays.put(SyntaxWalk.text(visit.node()), array && !nullable));
    }
    return arrays;
  }

  private static boolean isArrayParam(final NullModel.Param param) {
    final Optional<SyntaxNode> type = NullSyntax.first(param.node(), "typeType");
    return type.isPresent() && NullSyntax.isArray(type.get()) && !param.notes().nullable();
  }

  /** A call of {@code castToNonNull} on a value that is plainly not null. */
  static List<NullHit> castToNonnullArgNonnull(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            NullFlow.call(expression)
                .filter(call -> CAST_NON_NULL.equals(call.name()) && !call.args().isEmpty())
                .map(call -> call.args().get(0))
                .filter(NullCallFinders::plainlyNonNull)
                .ifPresent(arg -> hits.add(NullHit.at(arg, SyntaxWalk.text(arg))));
          }
        });
  }

  private static boolean plainlyNonNull(final SyntaxNode argument) {
    final String text = SyntaxWalk.text(argument);
    return text.startsWith("\"") || "this".equals(text) || text.startsWith(NEW);
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("pass-nullable", NullCallFinders::passNullable);
    action.accept("dereference-nullable", NullCallFinders::dereferenceNullable);
    action.accept(
        "assign-nullable-to-nonnull-array", NullCallFinders::assignNullableToNonnullArray);
    action.accept("cast-to-nonnull-arg-nonnull", NullCallFinders::castToNonnullArgNonnull);
  }
}
