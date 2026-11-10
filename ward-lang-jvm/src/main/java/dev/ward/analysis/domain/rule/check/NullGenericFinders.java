package dev.ward.analysis.domain.rule.check;

import dev.ward.analysis.domain.ir.SyntaxNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/** The rules about the nullness of type arguments, such as {@code List<@Nullable String>}. */
final class NullGenericFinders {

  private static final String LOCAL = "localVariableDeclaration";
  private static final String TYPE_TYPE = "typeType";

  private NullGenericFinders() {}

  /** A value with nullable type arguments stored where the declared type has none. */
  static List<NullHit> assignGenericNullable(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          if (!type.marked()) {
            return;
          }
          final Map<String, List<Boolean>> env = NullArgs.env(type, method);
          for (final SyntaxNode local : NullSyntax.scan(method.body(), Set.of(LOCAL))) {
            final Optional<List<Boolean>> declared =
                NullSyntax.first(local, TYPE_TYPE).flatMap(NullArgs::of);
            declared.ifPresent(flags -> widened(local, flags, env, hits));
          }
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            if (NullFlow.ASSIGN.equals(NullSyntax.operator(expression))) {
              final Optional<List<Boolean>> target =
                  Optional.ofNullable(env.get(NullSyntax.name(expression.children().get(0))));
              target.ifPresent(flags -> widen(expression.children().get(2), flags, env, hits));
            }
          }
        });
  }

  private static void widened(
      final SyntaxNode local,
      final List<Boolean> declared,
      final Map<String, List<Boolean>> env,
      final List<NullHit> hits) {
    for (final SyntaxNode value : initializers(local)) {
      widen(value, declared, env, hits);
    }
  }

  static List<SyntaxNode> initializers(final SyntaxNode local) {
    return SyntaxWalk.visits(local, Set.of("variableInitializer")).stream()
        .flatMap(visit -> visit.node().children().stream())
        .filter(node -> "expression".equals(node.kind()))
        .toList();
  }

  private static void widen(
      final SyntaxNode value,
      final List<Boolean> declared,
      final Map<String, List<Boolean>> env,
      final List<NullHit> hits) {
    NullArgs.ofValue(value, env)
        .filter(actual -> NullArgs.widens(declared, actual))
        .ifPresent(actual -> hits.add(NullHit.at(value, SyntaxWalk.text(value))));
  }

  /** A value with nullable type arguments passed for a parameter whose type has none. */
  static List<NullHit> passNullableGeneric(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          final Map<String, List<Boolean>> env = NullArgs.env(type, method);
          for (final SyntaxNode expression : NullSyntax.expressions(method.body())) {
            NullCallees.resolve(unit, type, expression)
                .filter(callee -> callee.owner().marked())
                .ifPresent(callee -> passed(callee, NullFlow.arguments(expression), env, hits));
          }
        });
  }

  private static void passed(
      final NullCallees.Callee callee,
      final List<SyntaxNode> args,
      final Map<String, List<Boolean>> env,
      final List<NullHit> hits) {
    final List<NullModel.Param> params = callee.method().params();
    for (int index = 0; index < args.size(); index++) {
      final SyntaxNode arg = args.get(index);
      NullSyntax.first(params.get(index).node(), TYPE_TYPE)
          .flatMap(NullArgs::of)
          .ifPresent(declared -> widen(arg, declared, env, hits));
    }
  }

  /** A returned value with nullable type arguments where the return type has none. */
  static List<NullHit> returnNullableGeneric(final NullFinder.Unit unit) {
    return NullValueFinders.methods(
        unit,
        (type, method, hits) -> {
          if (!type.marked()) {
            return;
          }
          final Optional<List<Boolean>> declared = returnArgs(method);
          final Map<String, List<Boolean>> env = NullArgs.env(type, method);
          declared.ifPresent(
              flags ->
                  NullFlow.returned(method.body())
                      .forEach(value -> widen(value, flags, env, hits)));
        });
  }

  static Optional<List<Boolean>> returnArgs(final NullModel.Method method) {
    return NullSyntax.first(method.node(), "typeTypeOrVoid")
        .flatMap(returned -> NullSyntax.first(returned, TYPE_TYPE))
        .flatMap(NullArgs::of);
  }

  /** Runs {@code action} for each rule of this class. */
  static void each(final BiConsumer<String, NullFinder> action) {
    action.accept("assign-generic-nullable", NullGenericFinders::assignGenericNullable);
    action.accept("pass-nullable-generic", NullGenericFinders::passNullableGeneric);
    action.accept("return-nullable-generic", NullGenericFinders::returnNullableGeneric);
  }
}
